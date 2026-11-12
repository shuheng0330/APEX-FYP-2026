package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ReviewPeriodClosureBlockedException;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.AnnualAppraisalReadinessService;
import com.tbm.careerpathlearning.service.AnnualReviewPeriodClosureService;
import com.tbm.careerpathlearning.service.AnnualReviewPeriodConfigurationValidator;
import com.tbm.careerpathlearning.service.KpiCheckpointAggregateService;
import jakarta.persistence.Entity;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Explicit disposable database only: never loads application profiles, .env or public fixtures. */
@EnabledIfEnvironmentVariable(named = "APEX_CLOSURE_POSTGRES_URL", matches = ".+")
class AnnualReviewPeriodClosurePostgresTest {
    private static final UUID ACTOR = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_ACTOR = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("Asia/Singapore"));
    private static final OffsetDateTime NOW = OffsetDateTime.now(CLOCK);
    private DriverManagerDataSource source;
    private JdbcTemplate admin;
    private JdbcTemplate db;
    private String schema;
    private SessionFactory sessionFactory;
    private AnnualKpiReviewPeriodRepository periods;
    private AnnualReviewPeriodClosureService service;
    private AnnualAppraisalReadinessService performance;
    private TransactionTemplate transaction;
    private ExecutorService workers;
    private final List<CountDownLatch> releases = new ArrayList<>();

    @BeforeEach
    void disposableSchema() {
        String url = System.getenv("APEX_CLOSURE_POSTGRES_URL");
        assertTrue(url.startsWith("jdbc:postgresql://"), "An explicit PostgreSQL JDBC URL is required");
        var uri = URI.create(url.substring(5));
        assertTrue(List.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost()),
                "Closure tests must use a local disposable PostgreSQL database");
        assertEquals(55441, uri.getPort(), "Use the dedicated closure-test cluster, never application PostgreSQL");
        assertTrue(uri.getPath().matches("/apex_closure_test_[a-z0-9_]+"),
                "Database name must start with apex_closure_test_");
        assertNull(uri.getRawQuery(), "Supply credentials separately; URL options are not accepted");
        assertNull(uri.getUserInfo());
        assertNull(uri.getFragment());
        String user = System.getenv("APEX_CLOSURE_POSTGRES_USER");
        assertNotNull(user, "Supply APEX_CLOSURE_POSTGRES_USER explicitly; application credentials are never read");
        assertFalse(user.isBlank());
        String password = System.getenv().getOrDefault("APEX_CLOSURE_POSTGRES_PASSWORD", "");
        var root = new DriverManagerDataSource(url, user, password);
        admin = new JdbcTemplate(root);
        assertEquals(uri.getPath().substring(1), admin.queryForObject("SELECT current_database()", String.class));
        schema = "closure_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE SCHEMA " + schema);
        source = new DriverManagerDataSource(url, user, password);
        var options = new Properties();
        options.setProperty("currentSchema", schema);
        options.setProperty("ApplicationName", schema);
        options.setProperty("options", "-c statement_timeout=30000 -c lock_timeout=20000");
        source.setConnectionProperties(options);
        db = new JdbcTemplate(source);
        db.execute("""
                CREATE TABLE staff(id UUID PRIMARY KEY, name TEXT, email TEXT, password TEXT,
                    role_id BIGINT, career_pathway_id BIGINT, manager_id UUID,
                    is_first_login BOOLEAN NOT NULL DEFAULT false, account_status TEXT DEFAULT 'ACTIVE',
                    is_deleted BOOLEAN NOT NULL DEFAULT false, created_by UUID, updated_by UUID,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now())
                """);
        db.update("INSERT INTO staff(id, name) VALUES (?, 'Closure HR'), (?, 'Second Closure HR')", ACTOR, SECOND_ACTOR);
        db.execute("""
                CREATE TABLE annual_kpi_review_period (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    reference_number VARCHAR(36) NOT NULL UNIQUE,
                    name VARCHAR(255), start_date DATE, end_date DATE,
                    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                    assessment_model VARCHAR(24) NOT NULL DEFAULT 'CENTRAL_RESULTS',
                    frequency_policy VARCHAR(24) NOT NULL DEFAULT 'ROLE_BASED',
                    kpi_performance_weight NUMERIC(5,2) DEFAULT 50,
                    attitude_evaluation_weight NUMERIC(5,2) DEFAULT 50,
                    annual_kpi_consolidation_method VARCHAR(30),
                    kpi_setup_deadline DATE, self_assessment_days_after_checkpoint INTEGER,
                    superior_assessment_days_after_self_deadline INTEGER,
                    attitude_self_assessment_start_date DATE, attitude_self_assessment_deadline DATE,
                    superior_attitude_evaluation_deadline DATE, appraisal_recommendation_deadline DATE,
                    hr_finalisation_deadline DATE, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), created_by UUID, updated_by UUID,
                    opened_at TIMESTAMPTZ, closed_at TIMESTAMPTZ, participants_snapshotted_at TIMESTAMPTZ,
                    kpi_amendments_enabled BOOLEAN NOT NULL DEFAULT false, kpi_amendments_close_date DATE,
                    kpi_amendments_changed_at TIMESTAMPTZ, kpi_amendments_changed_by UUID,
                    attitude_configuration_id BIGINT
                )
                """);
        db.update("""
                INSERT INTO annual_kpi_review_period(id, reference_number, name, start_date, end_date,
                    status, annual_kpi_consolidation_method, opened_at, participants_snapshotted_at)
                VALUES (1, ?, 'Disposable closure period', '2025-01-01', '2025-12-31',
                    'OPEN', 'FINAL_CHECKPOINT', '2025-01-01T00:00:00+08:00', '2025-01-01T00:00:00+08:00')
                """, UUID.randomUUID().toString());
    }

    @AfterEach
    void removeOwnSchema() throws InterruptedException {
        releases.forEach(CountDownLatch::countDown);
        if (workers != null) {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(20, TimeUnit.SECONDS), "Workers must stop before schema cleanup");
        }
        if (sessionFactory != null) sessionFactory.close();
        if (schema != null) {
            assertTrue(schema.matches("closure_[0-9a-f]{32}"));
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void v55PreservesHistoricalRowsWithoutInventingClosingActorsAndEnforcesStaffForeignKey() throws Exception {
        db.update("""
                INSERT INTO annual_kpi_review_period(id, reference_number, name, status, closed_at, updated_by)
                VALUES (2, ?, 'Previously closed', 'CLOSED', '2025-12-31T16:00:00Z', ?)
                """, UUID.randomUUID().toString(), ACTOR);
        var before = periodRows(false);
        try (var connection = source.getConnection()) {
            migrate(connection);
        }
        assertEquals(before, periodRows(true), "V55 must not rewrite any historical value");
        assertEquals(0, db.queryForObject("SELECT count(*) FROM annual_kpi_review_period WHERE closed_by IS NOT NULL", Integer.class));
        assertEquals("YES", db.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = ? AND table_name = 'annual_kpi_review_period' AND column_name = 'closed_by'
                """, String.class, schema));
        assertEquals("uuid", db.queryForObject("""
                SELECT udt_name FROM information_schema.columns
                WHERE table_schema = ? AND table_name = 'annual_kpi_review_period' AND column_name = 'closed_by'
                """, String.class, schema));
        db.update("UPDATE annual_kpi_review_period SET status = 'CLOSED', closed_at = '2026-01-01T00:00:00Z', closed_by = ? WHERE id = 1", ACTOR);
        assertEquals(ACTOR, db.queryForObject("SELECT closed_by FROM annual_kpi_review_period WHERE id = 1", UUID.class));
        try (var connection = source.getConnection(); var statement = connection.prepareStatement(
                "UPDATE annual_kpi_review_period SET closed_by = ? WHERE id = 1")) {
            statement.setObject(1, UUID.randomUUID());
            assertEquals("23503", assertThrows(SQLException.class, statement::executeUpdate).getSQLState());
        }
        assertEquals(ACTOR, db.queryForObject("SELECT closed_by FROM annual_kpi_review_period WHERE id = 1", UUID.class));
    }

    @Test
    void failedMigrationTransactionRollsBackDdlAndPreservesExistingRows() throws Exception {
        var before = periodRows(false);
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                migrate(connection);
                try (var statement = connection.createStatement()) {
                    assertThrows(SQLException.class, () -> statement.execute("SELECT 1 / 0"));
                }
            } finally {
                connection.rollback();
            }
        }
        assertEquals(0, db.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = ? AND table_name = 'annual_kpi_review_period' AND column_name = 'closed_by'
                """, Integer.class, schema));
        assertEquals(before, periodRows(false));
    }

    @Test
    void readinessUsesAReadOnlyTransactionAndDoesNotPersistAnything() throws Exception {
        prepareService();
        var before = periodRows(true);
        doAnswer(invocation -> {
            assertEquals("on", db.queryForObject("SELECT current_setting('transaction_read_only')", String.class));
            return recordedPerformance(invocation.getArgument(0));
        }).when(performance).evaluate(any());
        var ready = service.readiness(1L, ACTOR);
        assertTrue(ready.canClose());
        assertEquals(1, ready.completedEmployees());
        assertEquals(before, periodRows(true));
        assertEquals(0, transitionCount());
    }

    @Test
    void missingRecordedSourceInProxiedReadinessReturnsBlockedStateWithoutRollingBackOuterTransaction() throws Exception {
        prepareService(true);
        var before = periodRows(false);
        var evidenceBefore = db.queryForObject("SELECT to_jsonb(e)::text FROM closure_evidence e", String.class);
        var ready = assertDoesNotThrow(() -> service.readiness(1L, ACTOR),
                "An expected missing source must not cause UnexpectedRollbackException at the outer transaction boundary");
        assertFalse(ready.canClose());
        assertEquals(AnnualKpiReviewPeriodStatus.OPEN, ready.status());
        assertTrue(ready.blockers().stream().anyMatch(blocker -> blocker.code() == ReviewPeriodClosureDto.Code.CONFIGURATION
                && "Recorded Company source is unavailable".equals(blocker.message())));
        assertEquals(0, ready.completedEmployees());
        assertEquals(1, ready.attitudeEvaluations().completed());
        assertEquals(before, periodRows(false), "Readiness must preserve every stored period column, including closure metadata");
        assertEquals(evidenceBefore, db.queryForObject("SELECT to_jsonb(e)::text FROM closure_evidence e", String.class));
        assertEquals(0, transitionCount());
    }

    @Test
    void failedCloseRollsBackStatusActorTimestampAndTransitionAndReleasesConfigurationLock() throws Exception {
        prepareService();
        var before = periodRows(true);
        db.execute("""
                CREATE FUNCTION reject_closure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'Injected closure failure' USING ERRCODE = '23514';
                END $$
                """);
        db.execute("""
                CREATE TRIGGER zz_reject_closure AFTER UPDATE OF status ON annual_kpi_review_period
                FOR EACH ROW WHEN (NEW.status = 'CLOSED') EXECUTE FUNCTION reject_closure()
                """);
        var failure = assertThrows(RuntimeException.class, () -> service.close(1L, ACTOR));
        assertEquals("23514", sqlFailure(failure).getSQLState());
        assertEquals(before, periodRows(true));
        assertNull(db.queryForObject("SELECT closed_by FROM annual_kpi_review_period WHERE id = 1", UUID.class));
        assertEquals(0, transitionCount());
        assertConfigurationLockReleased();
        db.execute("DROP TRIGGER zz_reject_closure ON annual_kpi_review_period");
        assertEquals(AnnualKpiReviewPeriodStatus.CLOSED, service.close(1L, ACTOR).status(),
                "A rolled-back close must not poison later attempts");
        assertEquals(1, transitionCount());
    }

    @Test
    void twoConcurrentClosesProduceOneTransitionAndPreserveTheOriginalActorAndTimestamp() throws Exception {
        prepareService();
        var evaluating = new CountDownLatch(1);
        var release = releaseLatch();
        doAnswer(invocation -> {
            evaluating.countDown(); await(release);
            return recordedPerformance(invocation.getArgument(0));
        }).when(performance).evaluate(any());
        var first = async(() -> service.close(1L, ACTOR), new AtomicInteger());
        await(evaluating);
        var secondPid = new AtomicInteger();
        var second = async(() -> service.close(1L, SECOND_ACTOR), secondPid);
        awaitConfigurationWait(secondPid);
        release.countDown();
        var closed = first.get(20, TimeUnit.SECONDS);
        var repeated = second.get(20, TimeUnit.SECONDS);
        assertEquals(AnnualKpiReviewPeriodStatus.CLOSED, closed.status());
        assertEquals(closed.closedAt().toInstant(), repeated.closedAt().toInstant());
        assertEquals("Closure HR", repeated.closedByName());
        assertEquals(ACTOR, db.queryForObject("SELECT closed_by FROM annual_kpi_review_period WHERE id = 1", UUID.class));
        assertEquals(1, transitionCount(), "Only one Open-to-Closed transition may commit");
        var recorded = periodRows(true);
        service.close(1L, SECOND_ACTOR);
        assertEquals(recorded, periodRows(true), "A repeated close must not update the recorded closure");
        assertEquals(1, transitionCount());
    }

    @Test
    void performanceWriteWaitingForClosureCannotChangeFrozenResults() throws Exception {
        prepareService();
        var evaluating = new CountDownLatch(1);
        var release = releaseLatch();
        doAnswer(invocation -> {
            evaluating.countDown(); await(release);
            return recordedPerformance(invocation.getArgument(0));
        }).when(performance).evaluate(any());
        var closing = async(() -> service.close(1L, ACTOR), new AtomicInteger());
        await(evaluating);
        var writerPid = new AtomicInteger();
        var writing = async(() -> writePerformance(false), writerPid);
        awaitConfigurationWait(writerPid);
        release.countDown();
        assertEquals(AnnualKpiReviewPeriodStatus.CLOSED, closing.get(20, TimeUnit.SECONDS).status());
        var rejected = assertThrows(ExecutionException.class, () -> writing.get(20, TimeUnit.SECONDS)).getCause();
        assertInstanceOf(BadRequestException.class, rejected);
        assertTrue(evidenceComplete(), "The waiting writer must recheck period status under the shared lock");
        assertEquals(1, transitionCount());
    }

    @Test
    void closeWaitsForAnEarlierPerformanceWriteAndRevalidatesItsCommittedEvidence() throws Exception {
        prepareService();
        var written = new CountDownLatch(1);
        var commit = releaseLatch();
        var writing = async(() -> {
            var result = writePerformance(false); written.countDown(); await(commit); return result;
        }, new AtomicInteger());
        await(written);
        var closerPid = new AtomicInteger();
        var closing = async(() -> service.close(1L, ACTOR), closerPid);
        awaitConfigurationWait(closerPid);
        commit.countDown();
        writing.get(20, TimeUnit.SECONDS);
        var rejected = assertThrows(ExecutionException.class, () -> closing.get(20, TimeUnit.SECONDS)).getCause();
        var blocked = assertInstanceOf(ReviewPeriodClosureBlockedException.class, rejected);
        assertFalse(blocked.readiness().canClose());
        assertTrue(blocked.readiness().blockers().stream()
                .anyMatch(b -> b.code() == ReviewPeriodClosureDto.Code.KPI_SUPERIOR_REVIEW));
        assertEquals(1, blocked.readiness().attitudeEvaluations().completed(),
                "An incomplete KPI review must not reduce completed Attitude Evaluations");
        assertEquals("OPEN", db.queryForObject("SELECT status FROM annual_kpi_review_period WHERE id = 1", String.class));
        assertNull(db.queryForObject("SELECT closed_at FROM annual_kpi_review_period WHERE id = 1", OffsetDateTime.class));
        assertFalse(evidenceComplete());
        assertEquals(0, transitionCount());
        assertConfigurationLockReleased();
    }

    private void prepareService() throws Exception {
        prepareService(false);
    }

    private void prepareService(boolean useProxiedReadiness) throws Exception {
        try (var connection = source.getConnection()) { migrate(connection); }
        db.execute("""
                CREATE TABLE attitude_configuration(id BIGINT PRIMARY KEY, name TEXT, status VARCHAR(20),
                    created_at TIMESTAMPTZ, updated_at TIMESTAMPTZ, created_by UUID, updated_by UUID,
                    published_at TIMESTAMPTZ, published_by UUID)
                """);
        db.update("""
                INSERT INTO attitude_configuration VALUES
                    (501, 'Fictional published configuration', 'PUBLISHED', ?, ?, ?, ?, ?, ?)
                """, NOW, NOW, ACTOR, ACTOR, NOW, ACTOR);
        db.execute("""
                CREATE TABLE role(id BIGINT PRIMARY KEY, name VARCHAR(255) NOT NULL, description VARCHAR(1000),
                    is_visible BOOLEAN NOT NULL DEFAULT true, is_deleted BOOLEAN NOT NULL DEFAULT false,
                    performance_review_eligible BOOLEAN NOT NULL DEFAULT true, created_by UUID, updated_by UUID,
                    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL, org_chart_id BIGINT,
                    default_review_frequency VARCHAR(20), employee_level_id BIGINT)
                """);
        db.update("INSERT INTO role(id, name, created_at, updated_at) VALUES (7, 'Fictional role', ?, ?)", NOW, NOW);
        db.update("UPDATE staff SET role_id = 7");
        db.execute("""
                CREATE TABLE attitude_criterion(id BIGINT PRIMARY KEY,
                    configuration_id BIGINT NOT NULL REFERENCES attitude_configuration(id), name VARCHAR(255),
                    description TEXT, criterion_type VARCHAR(30) NOT NULL, evaluation_format VARCHAR(20),
                    active BOOLEAN NOT NULL, display_order INTEGER NOT NULL)
                """);
        db.update("""
                INSERT INTO attitude_criterion VALUES
                    (601, 501, 'Fictional criterion', 'Recorded evidence', 'SHARED_CORE_VALUE', NULL, true, 1)
                """);
        db.execute("""
                CREATE TABLE attitude_role_format_mapping(id BIGINT PRIMARY KEY,
                    configuration_id BIGINT NOT NULL REFERENCES attitude_configuration(id),
                    role_id BIGINT NOT NULL REFERENCES role(id), evaluation_format VARCHAR(20) NOT NULL,
                    UNIQUE(configuration_id, role_id))
                """);
        db.update("INSERT INTO attitude_role_format_mapping VALUES (801, 501, 7, 'OTHERS')");
        db.update("UPDATE annual_kpi_review_period SET attitude_configuration_id = 501 WHERE id = 1");
        db.execute("CREATE TABLE closure_evidence(period_id BIGINT PRIMARY KEY, complete BOOLEAN NOT NULL)");
        db.update("INSERT INTO closure_evidence VALUES (1, true)");
        db.execute("CREATE TABLE closure_transition(period_id BIGINT, actor UUID, closed_at TIMESTAMPTZ)");
        db.execute("""
                CREATE FUNCTION record_closure() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    INSERT INTO closure_transition VALUES (NEW.id, NEW.closed_by, NEW.closed_at);
                    RETURN NEW;
                END $$
                """);
        db.execute("""
                CREATE TRIGGER record_closure AFTER UPDATE OF status ON annual_kpi_review_period
                FOR EACH ROW WHEN (OLD.status <> 'CLOSED' AND NEW.status = 'CLOSED')
                EXECUTE FUNCTION record_closure()
                """);
        var configuration = new Configuration();
        configuration.getProperties().put("hibernate.connection.datasource", source);
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        configuration.setProperty("hibernate.default_schema", schema);
        configuration.setProperty("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_HOLD");
        configuration.setProperty("hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var candidate : scanner.findCandidateComponents("com.tbm.careerpathlearning.model")) {
            configuration.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
        }
        sessionFactory = configuration.buildSessionFactory();
        var manager = new JpaTransactionManager(sessionFactory);
        manager.setJpaDialect(new HibernateJpaDialect());
        manager.setDataSource(source);
        transaction = new TransactionTemplate(manager);
        var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(sessionFactory));
        periods = repositories.getRepository(AnnualKpiReviewPeriodRepository.class);
        var participants = mock(ReviewPeriodParticipantRepository.class);
        var checkpoints = mock(ReviewCheckpointRepository.class);
        var kpi = mock(KpiAssessmentRepository.class);
        var attitudes = mock(AttitudeAssessmentRepository.class);
        var staff = mock(StaffRepository.class);
        var permissions = mock(RoleAuthorityRepository.class);
        performance = mock(AnnualAppraisalReadinessService.class);
        when(staff.findById(ACTOR)).thenReturn(Optional.of(actor(ACTOR, "Closure HR")));
        when(staff.findById(SECOND_ACTOR)).thenReturn(Optional.of(actor(SECOND_ACTOR, "Second Closure HR")));
        when(permissions.existsByRoleIdAndAuthorityName(7L, AuthorityName.CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD)).thenReturn(true);
        when(participants.findAllByReviewPeriodId(1L)).thenAnswer(ignored -> List.of(participant()));
        when(checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(1L, ReviewFrequency.ANNUALLY))
                .thenAnswer(ignored -> List.of(checkpoint()));
        when(kpi.findPerformanceAssessments(List.of(101L))).thenAnswer(ignored -> {
            var assessment = new KpiAssessment();
            assessment.setId(201L); assessment.setParticipant(participant()); assessment.setCheckpoint(checkpoint());
            assessment.setReviewPeriodId(1L); assessment.setReviewFrequency(ReviewFrequency.ANNUALLY);
            assessment.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);
            assessment.setStatus(evidenceComplete() ? KpiAssessmentStatus.REVIEWED : KpiAssessmentStatus.PENDING_REVIEW);
            return List.of(assessment);
        });
        when(attitudes.findPerformanceAssessments(List.of(101L))).thenAnswer(ignored -> {
            var assessment = new AttitudeAssessment();
            assessment.setId(301L); assessment.setParticipant(participant());
            assessment.setReviewPeriodId(1L);
            assessment.setConfiguration(assessment.getParticipant().getReviewPeriod().getAttitudeConfiguration());
            assessment.setEvaluationFormat(AttitudeEvaluationFormat.OTHERS);
            assessment.setStatus(AttitudeAssessmentStatus.REVIEWED);
            assessment.setAttitudeScore(new BigDecimal("80.00"));
            assessment.setReviewedAt(NOW); assessment.setReviewedBy(ACTOR);
            var criterion = assessment.getConfiguration().getCriteria().stream()
                    .filter(candidate -> candidate.getId().equals(601L)).findFirst().orElseThrow();
            var item = new AttitudeAssessmentItem();
            item.setId(701L); item.setAssessment(assessment); item.setCriterion(criterion);
            item.setConfigurationId(501L); item.setSelfPoint(4); item.setSuperiorPoint(4);
            assessment.getItems().add(item);
            return List.of(assessment);
        });
        when(performance.evaluate(any())).thenAnswer(invocation -> recordedPerformance(invocation.getArgument(0)));
        AnnualAppraisalReadinessService readiness = performance;
        if (useProxiedReadiness) {
            when(kpi.findAllByParticipantId(101L)).thenAnswer(ignored -> kpi.findPerformanceAssessments(List.of(101L)));
            when(attitudes.findByParticipantId(101L)).thenAnswer(ignored ->
                    attitudes.findPerformanceAssessments(List.of(101L)).stream().findFirst());
            var aggregate = mock(KpiCheckpointAggregateService.class);
            when(aggregate.pendingLevels(any())).thenAnswer(ignored -> {
                assertEquals("on", db.queryForObject("SELECT current_setting('transaction_read_only')", String.class));
                throw new BadRequestException("Recorded Company source is unavailable");
            });
            var actual = new AnnualAppraisalReadinessService(checkpoints, kpi, attitudes, aggregate, mock(KpiPlanMapper.class));
            var readinessProxy = new ProxyFactory(actual);
            readinessProxy.setProxyTargetClass(true);
            readinessProxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
            readiness = (AnnualAppraisalReadinessService) readinessProxy.getProxy();
        }
        var target = new AnnualReviewPeriodClosureService(periods, participants, checkpoints, kpi, attitudes,
                readiness, new AnnualReviewPeriodConfigurationValidator(), staff, permissions, CLOCK);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (AnnualReviewPeriodClosureService) proxy.getProxy();
        workers = Executors.newFixedThreadPool(2);
    }

    private ReviewPeriodParticipant participant() {
        var period = periods.findById(1L).orElseThrow();
        var participant = new ReviewPeriodParticipant();
        participant.setId(101L); participant.setReviewPeriod(period);
        participant.setStaff(actor(ACTOR, "Fictional participant"));
        participant.setRole(participant.getStaff().getRole());
        participant.setStaffName("Fictional participant"); participant.setDepartmentName("Fictional department");
        participant.setReviewFrequency(ReviewFrequency.ANNUALLY);
        var allocation = new ReviewPeriodEmployeeLevelConfiguration();
        allocation.setReviewPeriod(period); allocation.setCompanyKpiWeight(BigDecimal.ZERO);
        allocation.setDepartmentKpiWeight(BigDecimal.ZERO); allocation.setIndividualKpiWeight(new BigDecimal("100"));
        participant.setEmployeeLevelConfiguration(allocation);
        return participant;
    }

    private ReviewCheckpoint checkpoint() {
        var checkpoint = new ReviewCheckpoint();
        checkpoint.setId(401L); checkpoint.setSequenceNumber(1);
        checkpoint.setReviewPeriod(periods.findById(1L).orElseThrow());
        checkpoint.setReviewFrequency(ReviewFrequency.ANNUALLY);
        checkpoint.setStartDate(LocalDate.of(2025, 1, 1)); checkpoint.setEndDate(LocalDate.of(2025, 12, 31));
        checkpoint.setSelfAssessmentDeadline(LocalDate.of(2026, 1, 7));
        checkpoint.setSuperiorAssessmentDeadline(LocalDate.of(2026, 1, 14));
        return checkpoint;
    }

    private AnnualAppraisalReadinessDto recordedPerformance(ReviewPeriodParticipant participant) {
        var score = evidenceComplete() ? new BigDecimal("80.00") : null;
        var item = new KpiItemDto(); item.setWeightage(new BigDecimal("100"));
        var breakdown = new KpiScoreBreakdownDto(201L, score, List.of(new KpiScoreBreakdownDto.Level(
                KpiLevel.INDIVIDUAL, new BigDecimal("100"), 1, null, NOW,
                KpiScoreBreakdownDto.Availability.AVAILABLE, score,
                List.of(new KpiScoreBreakdownDto.Item(item, null, 4)))));
        var checkpoint = checkpoint();
        var source = new AnnualAppraisalSnapshotDto.Checkpoint(checkpoint.getId(), 1, checkpoint.getStartDate(),
                checkpoint.getEndDate(), 201L, KpiAssessmentStatus.REVIEWED, score, NOW, List.of(), breakdown);
        var attitude = new AnnualAppraisalSnapshotDto.Attitude(301L, 501L, AttitudeEvaluationFormat.OTHERS,
                AttitudeAssessmentStatus.REVIEWED, NOW,
                List.of(new AnnualAppraisalSnapshotDto.Criterion(601L, "Fictional criterion", "Recorded evidence", 4)));
        var snapshot = new AnnualAppraisalSnapshotDto(participant.getId(), 1L, ACTOR, ReviewFrequency.ANNUALLY,
                AnnualKpiConsolidationMethod.FINAL_CHECKPOINT, new BigDecimal("50"), new BigDecimal("50"),
                score, new BigDecimal("80.00"), score, List.of(source), attitude);
        return new AnnualAppraisalReadinessDto(score != null,
                score == null ? List.of("Pending Superior review") : List.of(), snapshot);
    }

    private Staff actor(UUID id, String name) {
        var actor = new Staff(); actor.setId(id); actor.setName(name); actor.setAccountStatus(StaffAccountStatus.ACTIVE);
        var role = new Role(); role.setId(7L); actor.setRole(role);
        return actor;
    }

    // Models the existing performance-writer contract, not a replacement for its workflow tests.
    private boolean writePerformance(boolean complete) {
        periods.lockConfiguration();
        if (periods.findById(1L).orElseThrow().getStatus() != AnnualKpiReviewPeriodStatus.OPEN) {
            throw new BadRequestException("Closed Review Period results are read-only");
        }
        db.update("UPDATE closure_evidence SET complete = ? WHERE period_id = 1", complete);
        return complete;
    }

    private <T> Future<T> async(Supplier<T> action, AtomicInteger pid) {
        return workers.submit(() -> transaction.execute(status -> {
            pid.set(db.queryForObject("SELECT pg_backend_pid()", Integer.class));
            return action.get();
        }));
    }

    private CountDownLatch releaseLatch() {
        var latch = new CountDownLatch(1); releases.add(latch); return latch;
    }

    private void await(CountDownLatch latch) {
        try { assertTrue(latch.await(15, TimeUnit.SECONDS), "Timed out waiting for the coordinated transaction"); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new AssertionError("Transaction interrupted", interrupted);
        }
    }

    private void awaitConfigurationWait(AtomicInteger pid) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (pid.get() != 0 && Boolean.TRUE.equals(db.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM pg_locks WHERE pid = ? AND locktype = 'advisory'
                        AND classid = 20261006 AND objid = 1 AND NOT granted)
                    """, Boolean.class, pid.get()))) return;
            Thread.onSpinWait();
        }
        fail("The competing transaction did not wait on the existing configuration advisory lock");
    }

    private void assertConfigurationLockReleased() throws SQLException {
        try (var connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT pg_try_advisory_xact_lock(20261006, 1)")) {
                assertTrue(result.next()); assertTrue(result.getBoolean(1), "Transaction must release the configuration lock");
            } finally { connection.rollback(); }
        }
    }

    private boolean evidenceComplete() {
        return Boolean.TRUE.equals(db.queryForObject("SELECT complete FROM closure_evidence WHERE period_id = 1", Boolean.class));
    }

    private int transitionCount() { return db.queryForObject("SELECT count(*) FROM closure_transition", Integer.class); }

    private SQLException sqlFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) return sql;
        }
        throw new AssertionError("Expected the injected PostgreSQL failure", failure);
    }

    private List<String> periodRows(boolean migrated) {
        return db.queryForList("SELECT (to_jsonb(p)" + (migrated ? " - 'closed_by'" : "")
                + ")::text FROM annual_kpi_review_period p ORDER BY id", String.class);
    }

    private void migrate(Connection connection) throws Exception {
        var resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:db/migration/annual-kpi/V55__*.sql");
        assertEquals(1, resources.length, "Exactly one reviewed V55 migration must be available");
        try (var statement = connection.createStatement()) {
            statement.execute(resources[0].getContentAsString(StandardCharsets.UTF_8));
        }
    }
}
