package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.KpiVersionConflictException;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.KpiAssessmentEvidenceStorage;
import com.tbm.careerpathlearning.service.KpiCheckpointAggregateService;
import com.tbm.careerpathlearning.service.PerformanceDepartmentResolver;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.persistence.Entity;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.*;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real repositories and unchanged V46 guards; every schema and fixture is rollback-only. */
@EnabledIfEnvironmentVariable(named = "APEX_PHASE2_POSTGRES_TEST", matches = "true")
class CentralKpiResultPublicationPostgresTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-02-01T04:00:00Z"), ZoneId.of("Asia/Singapore"));
    private static final List<String> NEW_TABLES = List.of("kpi_central_result", "kpi_central_result_item",
            "kpi_central_result_evidence", "company_kpi_result_delegate", "kpi_checkpoint_result");
    private static final List<String> PRESERVED_TABLES = List.of("kpi_assessment", "kpi_assessment_item",
            "kpi_assessment_evidence", "kpi_assessment_plan_version", "employee_kpi_assignment", "kpi_checkpoint_result");
    @TempDir Path evidenceDirectory;
    private Connection connection;
    private SessionFactory sessionFactory;
    private Session session;
    private JpaRepositoryFactory repositories;
    private KpiCentralResultServiceImpl service;
    private KpiAssessmentEvidenceStorage storage;
    private String schema;
    private Staff owner;
    private Staff hod;
    private AnnualKpiReviewPeriod period;
    private OrgChart department;
    private Scope company;
    private Scope departmental;
    private final Map<String, Long> publicCounts = new LinkedHashMap<>();
    private final Map<String, String> preserved = new LinkedHashMap<>();
    private boolean expectAggregateUpdates;

    private record Scope(Long versionId, Long checkpointId, Long kpiId) {}

    @BeforeEach
    void isolatedSchemaAndRealService() throws Exception {
        var env = Dotenv.configure().directory(".").load();
        String url = env.get("DB_URL");
        assertTrue(List.of("localhost", "127.0.0.1", "::1").contains(URI.create(url.substring(5)).getHost()));
        connection = DriverManager.getConnection(url, env.get("DB_USER"), env.get("DB_PASS"));
        connection.setAutoCommit(false);
        for (String table : List.of("staff", "annual_kpi_review_period", "kpi_central_result", "kpi_assessment",
                "kpi_assessment_item", "kpi_assessment_evidence", "employee_kpi_assignment", "flyway_schema_history")) {
            publicCounts.put(table, count("public.\"" + table + "\""));
        }
        schema = "apex_central_publication_" + UUID.randomUUID().toString().replace("-", "");
        sql("CREATE SCHEMA " + schema);
        sql("SET LOCAL search_path TO " + schema);
        List<String> tables = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema='public' "
                        + "AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history' ORDER BY table_name")) {
            while (rows.next()) tables.add(rows.getString(1));
        }
        for (String table : tables) {
            if (!NEW_TABLES.contains(table)) {
                sql("CREATE TABLE " + quote(table) + " (LIKE public." + quote(table) + " INCLUDING ALL)");
            }
        }
        // LIKE copies serial defaults, unlike identity sequences. Never advance a public sequence.
        List<String[]> serialColumns = new ArrayList<>();
        try (var statement = connection.prepareStatement("SELECT table_name,column_name FROM information_schema.columns "
                + "WHERE table_schema=? AND column_default LIKE 'nextval(%'")) {
            statement.setString(1, schema);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) serialColumns.add(new String[]{rows.getString(1), rows.getString(2)});
            }
        }
        for (int i = 0; i < serialColumns.size(); i++) {
            String sequence = "fixture_serial_" + i;
            sql("CREATE SEQUENCE " + sequence);
            sql("ALTER TABLE " + quote(serialColumns.get(i)[0]) + " ALTER COLUMN " + quote(serialColumns.get(i)[1])
                    + " SET DEFAULT nextval('" + schema + "." + sequence + "'::regclass)");
        }
        // Reconstruct only the pre-V46 columns/check in empty private clones, then install the real guards.
        sql("ALTER TABLE annual_kpi_review_period DROP COLUMN assessment_model");
        sql("ALTER TABLE kpi_assessment DROP COLUMN assessment_model");
        sql("ALTER TABLE kpi_assessment DROP CONSTRAINT IF EXISTS ck_assessment_review");
        sql("ALTER TABLE kpi_assessment ADD CONSTRAINT ck_assessment_review CHECK ("
                + "(status<>'REVIEWED' AND reviewed_at IS NULL AND reviewed_by IS NULL AND reviewed_late IS NULL AND checkpoint_score IS NULL) OR "
                + "(status='REVIEWED' AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL AND reviewed_late IS NOT NULL "
                + "AND checkpoint_score BETWEEN 0 AND 100 AND checkpoint_score IS NOT NULL))");
        sql(new ClassPathResource("db/migration/annual-kpi/V46__central_kpi_result_foundation.sql")
                .getContentAsString(StandardCharsets.UTF_8));

        var configuration = new Configuration();
        configuration.setProperty("hibernate.connection.url", url);
        configuration.setProperty("hibernate.connection.username", env.get("DB_USER"));
        configuration.setProperty("hibernate.connection.password", env.get("DB_PASS"));
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        configuration.setProperty("hibernate.default_schema", schema);
        configuration.setProperty("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var candidate : scanner.findCandidateComponents("com.tbm.careerpathlearning.model")) {
            configuration.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
        }
        sessionFactory = configuration.buildSessionFactory();
        session = sessionFactory.withOptions().connection(connection).openSession();
        session.beginTransaction();
        repositories = new JpaRepositoryFactory(session);
        fixture();
        storage = new KpiAssessmentEvidenceStorage(evidenceDirectory.toString(), 10485760);
        service = service(CLOCK);
        authenticate(owner, "CAN_MANAGE_COMPANY_KPI");
        session.flush();
        for (String table : PRESERVED_TABLES) preserved.put(table, digest(table));
    }

    @AfterEach
    void alwaysRollbackEvenAfterSetupOrAssertionFailure() throws Exception {
        SecurityContextHolder.clearContext();
        try {
            if (session != null && session.isOpen() && !preserved.isEmpty()) {
                for (var entry : preserved.entrySet()) {
                    if (expectAggregateUpdates && List.of("kpi_assessment", "kpi_checkpoint_result").contains(entry.getKey())) continue;
                    assertEquals(entry.getValue(), digest(entry.getKey()), entry.getKey());
                }
            }
        } finally {
            try {
                if (session != null) {
                    try {
                        if (session.getTransaction().isActive()) session.getTransaction().rollback();
                    } finally {
                        session.close();
                    }
                }
            } finally {
                try {
                    if (sessionFactory != null) sessionFactory.close();
                } finally {
                    if (connection != null) {
                        try {
                            connection.rollback();
                            for (var entry : publicCounts.entrySet()) {
                                assertEquals(entry.getValue().longValue(), count("public." + quote(entry.getKey())), entry.getKey());
                            }
                            try (var statement = connection.prepareStatement(
                                    "SELECT count(*) FROM information_schema.schemata WHERE schema_name=?")) {
                                statement.setString(1, schema);
                                try (var rows = statement.executeQuery()) {
                                    rows.next(); assertEquals(0, rows.getLong(1));
                                }
                            }
                        } finally {
                            try { connection.rollback(); } finally { connection.close(); }
                        }
                    }
                }
            }
        }
    }

    @Test
    void publishesCompanyAndScopedDepartmentResultsWithoutChangingIndividualRecordsOrAggregates() {
        var draft = service.create(request(company, null, 4, "80 units"), owner.getId());
        assertEquals(KpiCentralResultStatus.DRAFT, draft.getStatus());
        assertTrue(draft.getAllowedActions().contains("PUBLISH"));
        var published = service.publish(draft.getId(), decision(draft, null), owner.getId());
        session.flush(); session.clear();
        var reloaded = service.get(published.getId(), owner.getId());
        assertEquals(KpiCentralResultStatus.PUBLISHED, reloaded.getStatus());
        assertEquals(owner.getId(), reloaded.getPublishedBy());
        assertEquals(CLOCK.instant(), reloaded.getPublishedAt().toInstant());
        assertEquals(published.getLockVersion(), reloaded.getLockVersion());
        assertTrue(published.getLockVersion() > draft.getLockVersion());
        assertEquals(4, reloaded.getItems().get(0).getPoint());
        assertFalse(reloaded.getAllowedActions().contains("UPDATE_DRAFT"));
        authenticate(hod, "CAN_MANAGE_DEPARTMENT_KPI");
        var departmentDraft = service.create(request(departmental, null, 3, "60 units"), hod.getId());
        var approved = service.publish(departmentDraft.getId(), decision(departmentDraft, null), hod.getId());
        session.flush(); session.clear();
        assertEquals(department.getId(), service.get(approved.getId(), hod.getId()).getDepartmentId());
        assertEquals(KpiLevel.DEPARTMENT, approved.getLevel());
        assertEquals(hod.getId(), approved.getPublishedBy());
    }

    @Test
    void publishedResultsCompleteAndCorrectOnlyTheCheckpointAggregate() {
        expectAggregateUpdates = true;
        session.flush(); session.clear();
        var aggregateService = new KpiCheckpointAggregateService(
                repositories.getRepository(KpiCheckpointResultRepository.class),
                repositories.getRepository(KpiCentralResultRepository.class),
                repositories.getRepository(KpiAssessmentRepository.class),
                repositories.getRepository(EmployeeKpiAssignmentRepository.class), CLOCK,
                org.mapstruct.factory.Mappers.getMapper(com.tbm.careerpathlearning.mapper.KpiPlanMapper.class),
                new com.tbm.careerpathlearning.service.CompanyKpiCheckpointResolver(repositories.getRepository(ReviewCheckpointRepository.class)),mock(org.springframework.context.ApplicationEventPublisher.class));
        service = service(CLOCK, aggregateService);
        var companyDraft = service.create(request(company, null, 4, "80 units"), owner.getId());
        var first = service.publish(companyDraft.getId(), decision(companyDraft, null), owner.getId());
        session.flush(); session.clear();
        var assessmentRepository = repositories.getRepository(KpiAssessmentRepository.class);
        var assessment = assessmentRepository.findAllByReviewPeriodIdAndCheckpointIdAndStatus(
                period.getId(), company.checkpointId(), KpiAssessmentStatus.REVIEWED).get(0);
        assertNull(assessment.getCheckpointScore());
        var aggregateRepository = repositories.getRepository(KpiCheckpointResultRepository.class);
        var aggregate = aggregateRepository.lockByScope(assessment.getParticipant().getId(), company.checkpointId()).orElseThrow();
        assertNull(aggregate.getScore());
        assertNull(aggregate.getCalculatedAt());
        assertEquals(first.getId(), aggregate.getCompanyResultId());
        assertTrue(aggregate.getCalculationHistory().isEmpty());

        authenticate(hod, "CAN_MANAGE_DEPARTMENT_KPI");
        var departmentDraft = service.create(request(departmental, null, 3, "60 units"), hod.getId());
        service.publish(departmentDraft.getId(), decision(departmentDraft, null), hod.getId());
        session.flush(); session.clear();
        assessment = assessmentRepository.findAllByReviewPeriodIdAndCheckpointIdAndStatus(
                period.getId(), company.checkpointId(), KpiAssessmentStatus.REVIEWED).get(0);
        assertEquals(0, new BigDecimal("75.0000").compareTo(assessment.getCheckpointScore()));
        aggregate = aggregateRepository.lockByScope(assessment.getParticipant().getId(), company.checkpointId()).orElseThrow();
        assertEquals(1, aggregate.getCalculationHistory().size());
        assertNotNull(aggregate.getCalculatedAt());
        assertEquals(5, assessment.getItems().get(0).getSelfPoint());
        assertEquals(4, assessment.getItems().get(0).getSuperiorPoint());

        authenticate(owner, "CAN_MANAGE_COMPANY_KPI");
        var correction = service.correct(first.getId(), decision(first, "Correct measured result"), owner.getId());
        var changed = service.update(correction.getId(), request(company, correction.getLockVersion(), 5, "100 units"), owner.getId());
        var publishedCorrection = service.publish(changed.getId(), decision(changed, null), owner.getId());
        session.flush(); session.clear();
        assessment = assessmentRepository.findAllByReviewPeriodIdAndCheckpointIdAndStatus(
                period.getId(), company.checkpointId(), KpiAssessmentStatus.REVIEWED).get(0);
        assertEquals(0, new BigDecimal("78.0000").compareTo(assessment.getCheckpointScore()));
        aggregate = aggregateRepository.lockByScope(assessment.getParticipant().getId(), company.checkpointId()).orElseThrow();
        assertEquals(2, aggregate.getCalculationHistory().size());
        assertEquals(first.getId(), aggregate.getCalculationHistory().get(0).companyResultId());
        assertEquals(publishedCorrection.getId(), aggregate.getCalculationHistory().get(1).companyResultId());
        assertEquals(5, assessment.getItems().get(0).getSelfPoint());
        assertEquals(4, assessment.getItems().get(0).getSuperiorPoint());
    }

    @Test
    void correctionCopiesIndependentEvidenceAndSupersedesOnlyWhenPublished() throws Exception {
        var draft = service.create(request(company, null, 4, "80 units"), owner.getId());
        byte[] png = png();
        var upload = service.upload(draft.getItems().get(0).getId(), draft.getLockVersion(),
                new MockMultipartFile("file", "proof.png", "image/png", png), owner.getId());
        draft = service.get(draft.getId(), owner.getId());
        assertEquals(upload.resultLockVersion(), draft.getLockVersion());
        var published = service.publish(draft.getId(), decision(draft, null), owner.getId());
        var correction = service.correct(published.getId(), decision(published, "  Correct the measured result  "), owner.getId());
        assertEquals(2, correction.getEditionNumber());
        assertEquals(published.getId(), correction.getPreviousEditionId());
        assertEquals("Correct the measured result", correction.getCorrectionReason());
        assertEquals(KpiCentralResultStatus.PUBLISHED, service.get(published.getId(), owner.getId()).getStatus());
        assertEquals(correction.getId(), service.get(published.getId(), owner.getId()).getWorkingCorrectionId());
        var originalEvidence = published.getItems().get(0).getEvidence().get(0);
        var copiedEvidence = correction.getItems().get(0).getEvidence().get(0);
        assertNotEquals(originalEvidence.getId(), copiedEvidence.getId());
        assertEquals(originalEvidence.getUploadedBy(), copiedEvidence.getUploadedBy());
        assertEquals(originalEvidence.getUploadedAt().toInstant(), copiedEvidence.getUploadedAt().toInstant());
        var evidenceRows = repositories.getRepository(KpiCentralResultEvidenceRepository.class);
        assertNotEquals(evidenceRows.findById(originalEvidence.getId()).orElseThrow().getStorageKey(),
                evidenceRows.findById(copiedEvidence.getId()).orElseThrow().getStorageKey());
        assertArrayEquals(png, bytes(service.download(copiedEvidence.getId(), owner.getId())));
        var removed = service.deleteEvidence(copiedEvidence.getId(), correction.getLockVersion(), owner.getId());
        correction = service.update(correction.getId(), request(company, removed.resultLockVersion(), 5, "100 units"), owner.getId());
        var corrected = service.publish(correction.getId(), decision(correction, null), owner.getId());
        session.flush(); session.clear();
        var original = service.get(published.getId(), owner.getId());
        assertEquals(KpiCentralResultStatus.SUPERSEDED, original.getStatus());
        assertEquals(4, original.getItems().get(0).getPoint());
        assertEquals("80 units", original.getItems().get(0).getActualAchievement());
        assertEquals(published.getPublishedAt().toInstant(), original.getPublishedAt().toInstant());
        assertEquals(published.getUpdatedAt().toInstant(), original.getUpdatedAt().toInstant());
        assertArrayEquals(png, bytes(service.download(originalEvidence.getId(), owner.getId())));
        assertEquals(KpiCentralResultStatus.PUBLISHED, corrected.getStatus());
        assertEquals(5, corrected.getItems().get(0).getPoint());
        assertTrue(corrected.getItems().get(0).getEvidence().isEmpty());
        assertEquals(corrected.getLockVersion(), service.get(corrected.getId(), owner.getId()).getLockVersion());
    }

    @Test
    void repeatedCorrectionsKeepOrderedHistoryAndRejectDuplicateDraftsAndStaleClientTokens() {
        var draft = service.create(request(company, null, 4, "80 units"), owner.getId());
        var first = service.publish(draft.getId(), decision(draft, null), owner.getId());
        var secondDraft = service.correct(first.getId(), decision(first, "First correction"), owner.getId());
        assertThrows(BadRequestException.class,
                () -> service.correct(first.getId(), decision(first, "Competing correction"), owner.getId()));
        var changed = service.update(secondDraft.getId(), request(company, secondDraft.getLockVersion(), 3, "60 units"), owner.getId());
        assertThrows(KpiVersionConflictException.class,
                () -> service.publish(changed.getId(), decision(secondDraft, null), owner.getId()));
        var second = service.publish(changed.getId(), decision(changed, null), owner.getId());
        session.clear();
        var thirdDraft = service.correct(second.getId(), decision(second, "Second correction"), owner.getId());
        var third = service.publish(thirdDraft.getId(), decision(thirdDraft, null), owner.getId());
        session.flush(); session.clear();
        var history = service.history(third.getId(), owner.getId());
        assertEquals(List.of(3, 2, 1), history.stream().map(KpiCentralResultDto::getEditionNumber).toList());
        assertEquals(List.of(KpiCentralResultStatus.PUBLISHED, KpiCentralResultStatus.SUPERSEDED,
                KpiCentralResultStatus.SUPERSEDED), history.stream().map(KpiCentralResultDto::getStatus).toList());
        assertEquals(second.getId(), history.get(0).getPreviousEditionId());
        assertEquals(first.getId(), history.get(1).getPreviousEditionId());
        assertEquals("Second correction", history.get(0).getCorrectionReason());
        assertThrows(BadRequestException.class, () -> service.publish(third.getId(), decision(third, null), owner.getId()));
    }

    @Test
    void actualMigrationGuardsRejectPublishedContentAuditAndEvidenceWrites() throws Exception {
        var draft = service.create(request(company, null, 4, "80 units"), owner.getId());
        service.upload(draft.getItems().get(0).getId(), draft.getLockVersion(),
                new MockMultipartFile("file", "proof.png", "image/png", png()), owner.getId());
        draft = service.get(draft.getId(), owner.getId());
        var published = service.publish(draft.getId(), decision(draft, null), owner.getId());
        Long item = published.getItems().get(0).getId();
        Long evidence = published.getItems().get(0).getEvidence().get(0).getId();
        reject("23514", "UPDATE kpi_central_result SET updated_by='" + hod.getId() + "' WHERE id=" + published.getId());
        reject("23514", "DELETE FROM kpi_central_result WHERE id=" + published.getId());
        reject("23514", "UPDATE kpi_central_result_item SET point=1 WHERE id=" + item);
        reject("23514", "DELETE FROM kpi_central_result_item WHERE id=" + item);
        reject("23514", "UPDATE kpi_central_result_evidence SET original_filename='changed.png' WHERE id=" + evidence);
        reject("23514", "DELETE FROM kpi_central_result_evidence WHERE id=" + evidence);
        var correction = service.correct(published.getId(), decision(published, "Correct measured result"), owner.getId());
        reject("23505", "UPDATE kpi_central_result SET status='PUBLISHED',published_by=created_by,published_at=created_at WHERE id=" + correction.getId());
        reject("23514", "UPDATE kpi_central_result_item SET result_id=" + published.getId()
                + " WHERE id=" + correction.getItems().get(0).getId());
        assertEquals(4, service.get(published.getId(), owner.getId()).getItems().get(0).getPoint());
    }

    @Test
    void failedCorrectionPublicationRollsBackSupersessionAndPreservesOriginalPublication() throws Exception {
        var draft = service.create(request(company, null, 4, "80 units"), owner.getId());
        var first = service.publish(draft.getId(), decision(draft, null), owner.getId());
        var correction = service.correct(first.getId(), decision(first, "Correct actual achievement"), owner.getId());
        correction = service.update(correction.getId(), request(company, correction.getLockVersion(), 5, "100 units"), owner.getId());
        sql("CREATE FUNCTION fail_test_publication() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                + "IF NEW.status='PUBLISHED' AND NEW.edition_number>1 THEN "
                + "RAISE EXCEPTION 'Injected publication failure' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
        sql("CREATE TRIGGER zz_fail_test_publication BEFORE UPDATE ON kpi_central_result "
                + "FOR EACH ROW EXECUTE FUNCTION fail_test_publication()");
        session.flush();
        var savepoint = connection.setSavepoint();
        var ready = correction;
        try {
            var failure = assertThrows(RuntimeException.class,
                    () -> service.publish(ready.getId(), decision(ready, null), owner.getId()));
            assertTrue(sqlFailure(failure).getMessage().contains("Injected publication failure"));
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
            // Failed flush leaves stale managed state. Reload only after the JDBC savepoint is restored.
            session.clear();
        }
        var restored = session.find(KpiCentralResult.class, first.getId());
        session.refresh(restored);
        assertEquals(KpiCentralResultStatus.PUBLISHED, restored.getStatus());
        assertEquals(first.getPublishedAt().toInstant(), restored.getPublishedAt().toInstant());
        assertEquals(first.getLockVersion(), restored.getLockVersion());
        var stillDraft = session.find(KpiCentralResult.class, ready.getId());
        session.refresh(stillDraft);
        assertEquals(KpiCentralResultStatus.DRAFT, stillDraft.getStatus());
        assertNull(stillDraft.getPublishedAt());
        assertEquals(ready.getLockVersion(), stillDraft.getLockVersion());
        assertEquals(1, scalar("SELECT count(*) FROM kpi_central_result WHERE status='PUBLISHED'"));
        assertEquals(0, scalar("SELECT count(*) FROM kpi_central_result WHERE status='SUPERSEDED'"));
    }

    @Test
    void scopeAndDelegateRevocationAreEnforcedWithRealRepositories() {
        var companyDraft = service.create(request(company, null, 4, "80 units"), owner.getId());
        var delegation = service.assignDelegate(period.getId(), hod.getId(), owner.getId());
        authenticate(hod, "CAN_ENTER_COMPANY_KPI_RESULTS");
        var published = service.publish(companyDraft.getId(), decision(companyDraft, null), hod.getId());
        assertEquals(hod.getId(), published.getPublishedBy());
        authenticate(owner, "CAN_MANAGE_COMPANY_KPI");
        service.revokeDelegate(period.getId(), delegation.getId(), owner.getId());
        session.clear();
        authenticate(hod, "CAN_ENTER_COMPANY_KPI_RESULTS");
        assertThrows(AccessDeniedException.class, () -> service.get(published.getId(), hod.getId()));
        authenticate(hod, "CAN_MANAGE_DEPARTMENT_KPI");
        assertThrows(AccessDeniedException.class, () -> service.history(published.getId(), hod.getId()));
        var draft = service.create(request(departmental, null, 3, "60 units"), hod.getId());
        var foreignItem = request(departmental, draft.getLockVersion(), 4, "80 units");
        foreignItem.getItems().get(0).setKpiId(company.kpiId());
        assertThrows(BadRequestException.class, () -> service.update(draft.getId(), foreignItem, hod.getId()));
        assertEquals(3, service.get(draft.getId(), hod.getId()).getItems().get(0).getPoint());
        authenticate(owner, "CAN_MANAGE_COMPANY_KPI");
        assertThrows(AccessDeniedException.class, () -> service.get(draft.getId(), owner.getId()));
        assertEquals(KpiCentralResultStatus.PUBLISHED, service.get(published.getId(), owner.getId()).getStatus());
    }

    private KpiCentralResultServiceImpl service(Clock clock) {
        return service(clock, mock(KpiCheckpointAggregateService.class));
    }

    private KpiCentralResultServiceImpl service(Clock clock, KpiCheckpointAggregateService aggregateService) {
        var plans = Mappers.getMapper(KpiPlanMapper.class);
        var mapper = Mappers.getMapper(KpiCentralResultMapper.class);
        ReflectionTestUtils.setField(mapper, "kpiPlanMapper", plans);
        var resolver = mock(PerformanceDepartmentResolver.class);
        when(resolver.resolve(any(Role.class))).thenReturn(department);
        return new KpiCentralResultServiceImpl(repositories.getRepository(AnnualKpiReviewPeriodRepository.class),
                repositories.getRepository(ReviewCheckpointRepository.class), repositories.getRepository(KpiPlanVersionRepository.class),
                repositories.getRepository(KpiCentralResultRepository.class), repositories.getRepository(CompanyKpiResultDelegateRepository.class),
                repositories.getRepository(StaffRepository.class), resolver, mapper, plans,
                Mappers.getMapper(AnnualKpiReviewPeriodMapper.class), clock,
                repositories.getRepository(KpiCentralResultItemRepository.class), repositories.getRepository(KpiCentralResultEvidenceRepository.class), storage,
                aggregateService);
    }

    private void fixture() {
        var now = OffsetDateTime.now(CLOCK);
        department = new OrgChart(); department.setName("Isolated HR"); department.setType(OrgChartType.D);
        department.setCreatedAt(now); department.setUpdatedAt(now); session.persist(department);
        var level = new EmployeeLevel(); level.setCode("ISOLATED"); level.setName("Isolated Executive"); level.setDisplayOrder(1);
        level.setDefaultCompanyKpiWeight(new BigDecimal("15")); level.setDefaultDepartmentKpiWeight(new BigDecimal("25"));
        level.setDefaultIndividualKpiWeight(new BigDecimal("60")); session.persist(level);
        var role = new Role(); role.setName("Isolated HR Executive"); role.setOrgChart(department); role.setEmployeeLevel(level);
        role.setPerformanceReviewEligible(true); role.setDefaultReviewFrequency(ReviewFrequency.MONTHLY);
        role.setCreatedAt(now); role.setUpdatedAt(now); session.persist(role);
        owner = actor("Company owner", null); hod = actor("Department owner", role);
        var employee = actor("Individual employee", role); employee.setManager(hod);
        period = new AnnualKpiReviewPeriod();
        Mappers.getMapper(AnnualKpiReviewPeriodMapper.class).updateConfiguration(AnnualKpiReviewPeriodServiceImplTest.validRequest(), period);
        period.setName("Isolated central publication"); period.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);
        session.persist(period); session.flush();
        period.setStatus(AnnualKpiReviewPeriodStatus.OPEN); period.setOpenedAt(now); period.setParticipantsSnapshottedAt(now);
        session.flush();
        var checkpoint = new ReviewCheckpoint(); checkpoint.setReviewPeriod(period); checkpoint.setReviewFrequency(ReviewFrequency.MONTHLY);
        checkpoint.setSequenceNumber(1); checkpoint.setStartDate(LocalDate.of(2027, 1, 1)); checkpoint.setEndDate(LocalDate.of(2027, 1, 31));
        checkpoint.setSelfAssessmentDeadline(LocalDate.of(2027, 2, 5)); checkpoint.setSuperiorAssessmentDeadline(LocalDate.of(2027, 2, 10));
        session.persist(checkpoint);
        var weights = new ReviewPeriodEmployeeLevelConfiguration(); weights.setReviewPeriod(period); weights.setEmployeeLevel(level);
        weights.setCompanyKpiWeight(new BigDecimal("15")); weights.setDepartmentKpiWeight(new BigDecimal("25"));
        weights.setIndividualKpiWeight(new BigDecimal("60")); session.persist(weights); session.flush();
        var participant = new ReviewPeriodParticipant(); participant.setReviewPeriod(period); participant.setStaff(employee);
        participant.setRole(role); participant.setRoleName(role.getName()); participant.setDepartment(department);
        participant.setDepartmentName(department.getName()); participant.setSuperior(hod); participant.setStaffName(employee.getName());
        participant.setReviewFrequency(ReviewFrequency.MONTHLY); participant.setEmployeeLevelConfiguration(weights);
        participant.setEmployeeLevelConfigurationId(weights.getId()); participant.setCreatedAt(now); session.persist(participant);
        company = confirmed(KpiLevel.COMPANY, checkpoint, participant);
        departmental = confirmed(KpiLevel.DEPARTMENT, checkpoint, participant);
        var individual = confirmed(KpiLevel.INDIVIDUAL, checkpoint, participant);
        var assignment = repositories.getRepository(EmployeeKpiAssignmentRepository.class).findAll().stream()
                .filter(a -> a.getKpiId().equals(individual.kpiId())).findFirst().orElseThrow();
        var assessment = new KpiAssessment(); assessment.setParticipant(participant); assessment.setCheckpoint(checkpoint);
        assessment.setReviewPeriodId(period.getId()); assessment.setReviewFrequency(ReviewFrequency.MONTHLY);
        assessment.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS); assessment.setStatus(KpiAssessmentStatus.REVIEWED);
        assessment.setCreatedAt(now); assessment.setUpdatedAt(now); assessment.setCreatedBy(employee.getId()); assessment.setUpdatedBy(hod.getId());
        assessment.setSubmittedAt(now); assessment.setSubmittedBy(employee.getId()); assessment.setSubmittedToSuperiorId(hod.getId());
        assessment.setSubmittedLate(false); assessment.setReviewedAt(now); assessment.setReviewedBy(hod.getId()); assessment.setReviewedLate(false);
        session.persist(assessment);
        var answer = new KpiAssessmentItem(); answer.setAssessment(assessment); answer.setAssignment(assignment);
        answer.setParticipantId(participant.getId()); answer.setReviewPeriodId(period.getId());
        answer.setSelfPoint(5); answer.setSelfComment("Preserved self answer"); answer.setSuperiorPoint(4); answer.setSuperiorComment("Preserved review");
        session.persist(answer);
        for (Scope scope : List.of(company, departmental, individual)) {
            var binding = new KpiAssessmentPlanVersion(); binding.setAssessment(assessment); binding.setReviewPeriodId(period.getId());
            var version = session.find(KpiPlanVersion.class, scope.versionId()); binding.setVersion(version); binding.setLevel(version.getLevel());
            session.persist(binding);
        }
        var evidence = new KpiAssessmentEvidence(); evidence.setItem(answer); evidence.setStorageKey(UUID.randomUUID().toString());
        evidence.setOriginalFilename("preserved.png"); evidence.setContentType("image/png"); evidence.setSizeBytes(1L);
        evidence.setUploadedBy(employee.getId()); evidence.setUploadedAt(now); session.persist(evidence);
        session.flush();
    }

    private Scope confirmed(KpiLevel level, ReviewCheckpoint checkpoint, ReviewPeriodParticipant participant) {
        var now = OffsetDateTime.now(CLOCK);
        var plan = new KpiPlan(); plan.setReviewPeriod(period); plan.setLevel(level);
        if (level == KpiLevel.DEPARTMENT) plan.setDepartment(department);
        if (level == KpiLevel.INDIVIDUAL) plan.setOwnerParticipantId(participant.getId());
        plan.setCreatedBy(owner.getId()); plan.setUpdatedBy(owner.getId()); plan.setCreatedAt(now); plan.setUpdatedAt(now); session.persist(plan);
        var version = new KpiPlanVersion(); version.setPlan(plan); version.setReviewPeriodId(period.getId()); version.setLevel(level);
        version.setVersionNumber(1); version.setCreatedBy(owner.getId()); version.setUpdatedBy(owner.getId());
        version.setCreatedAt(now); version.setUpdatedAt(now); session.persist(version);
        var kpi = new Kpi(); kpi.setPlan(plan); kpi.setPlanId(plan.getId()); kpi.setVersion(version); kpi.setReviewPeriodId(period.getId());
        kpi.setName(level + " target"); kpi.setTarget("100 units"); kpi.setWeightage(new BigDecimal("100"));
        for (int point = 1; point <= 5; point++) kpi.getScoringDefinitions().put(point, "Definition " + point);
        session.persist(kpi); version.getItems().add(kpi); plan.setCurrentVersion(version); session.flush();
        version.setStatus(KpiVersionStatus.EFFECTIVE); version.setEffectiveAt(now);
        if (level == KpiLevel.COMPANY) {
            plan.setStatus(KpiPlanStatus.PUBLISHED); plan.setPublishedBy(owner.getId()); plan.setPublishedAt(now); plan.setPublishedLate(false);
        } else {
            plan.setStatus(KpiPlanStatus.APPROVED); plan.setSubmittedBy(hod.getId()); plan.setSubmittedAt(now); plan.setSubmittedLate(false);
            plan.setReviewedBy(owner.getId()); plan.setReviewedAt(now); plan.setReviewedLate(false);
            if (level == KpiLevel.INDIVIDUAL) plan.setSubmittedToSuperiorId(hod.getId());
        }
        session.flush();
        var assignment = new EmployeeKpiAssignment(); assignment.setKpiId(kpi.getId()); assignment.setParticipantId(participant.getId());
        assignment.setReviewPeriodId(period.getId()); assignment.setAssignedAt(now); session.persist(assignment); session.flush();
        return new Scope(version.getId(), checkpoint.getId(), kpi.getId());
    }

    private Staff actor(String name, Role role) {
        var actor = new Staff(); actor.setId(UUID.randomUUID()); actor.setName(name); actor.setRole(role);
        actor.setEmail(actor.getId() + "@example.test"); actor.setAccountStatus(StaffAccountStatus.ACTIVE);
        actor.setCreatedAt(OffsetDateTime.now(CLOCK)); actor.setUpdatedAt(actor.getCreatedAt()); session.persist(actor); return actor;
    }

    private void authenticate(Staff actor, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor.getId(), null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private KpiCentralResultRequest request(Scope scope, Long token, Integer point, String achievement) {
        var request = new KpiCentralResultRequest(); request.setPlanVersionId(scope.versionId()); request.setCheckpointId(scope.checkpointId());
        request.setExpectedLockVersion(token);
        var answer = new KpiCentralResultRequest.Answer(); answer.setKpiId(scope.kpiId()); answer.setPoint(point);
        answer.setActualAchievement(achievement); answer.setComment("Checked measurement"); request.setItems(List.of(answer)); return request;
    }

    private KpiCentralResultDecisionRequest decision(KpiCentralResultDto result, String reason) {
        var request = new KpiCentralResultDecisionRequest(); request.setExpectedLockVersion(result.getLockVersion());
        request.setReason(reason); return request;
    }

    private byte[] png() throws Exception {
        try (var output = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output)); return output.toByteArray();
        }
    }

    private byte[] bytes(com.tbm.careerpathlearning.service.KpiCentralResultService.Download download) throws Exception {
        try (var input = download.resource().getInputStream()) { return input.readAllBytes(); }
    }

    private void reject(String state, String statement) throws Exception {
        var savepoint = connection.setSavepoint();
        try { assertEquals(state, assertThrows(SQLException.class, () -> sql(statement)).getSQLState()); }
        finally { connection.rollback(savepoint); connection.releaseSavepoint(savepoint); }
    }

    private SQLException sqlFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException) return sqlException;
        }
        throw new AssertionError("Expected PostgreSQL failure", failure);
    }

    private String digest(String table) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT md5(coalesce(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM " + quote(table) + " t")) {
            rows.next(); return rows.getString(1);
        }
    }

    private long count(String table) throws SQLException { return scalar("SELECT count(*) FROM " + table); }
    private long scalar(String query) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) { rows.next(); return rows.getLong(1); }
    }
    private void sql(String query) throws SQLException { try (var statement = connection.createStatement()) { statement.execute(query); } }
    private String quote(String identifier) { return "\"" + identifier.replace("\"", "\"\"") + "\""; }
}
