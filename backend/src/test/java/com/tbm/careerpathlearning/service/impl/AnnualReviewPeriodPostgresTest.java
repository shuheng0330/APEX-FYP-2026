package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.mapper.AnnualKpiReviewPeriodMapper;
import com.tbm.careerpathlearning.service.AnnualReviewPeriodConfigurationValidator;
import com.tbm.careerpathlearning.service.ReviewCheckpointGenerator;
import com.tbm.careerpathlearning.service.ReviewPeriodParticipantFactory;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.persistence.Entity;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "APEX_PHASE1_POSTGRES_TEST", matches = "true")
class AnnualReviewPeriodPostgresTest {
    @Test
    void migrationAndJpaRepositoriesWorkWithoutChangingInheritedData() throws Exception {
        var env = Dotenv.configure().directory(".").load();
        String url = env.get("DB_URL");
        assertThat(URI.create(url.substring(5)).getHost()).isIn("localhost", "127.0.0.1", "::1");
        String schema = "apex_phase1_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, env.get("DB_USER"), env.get("DB_PASS"))) {
            connection.setAutoCommit(false);
            long staffCount = count(connection, "public.staff");
            long roleCount = count(connection, "public.role");
            long historyCount = count(connection, "public.flyway_schema_history");
            try {
                execute(connection, "CREATE SCHEMA " + schema);
                execute(connection, "SET LOCAL search_path TO " + schema);
                for (String table : List.of("org_chart", "role", "staff")) {
                    execute(connection, "CREATE TABLE " + schema + "." + table
                            + " (LIKE public." + table + " INCLUDING ALL)");
                }
                // The integration test also works after V27 is installed in public.
                execute(connection, "ALTER TABLE " + schema + ".role DROP COLUMN IF EXISTS default_review_frequency");
                ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                        "db/migration/annual-kpi/V27__annual_review_period_foundation.sql"));

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
                try (var sessionFactory = configuration.buildSessionFactory();
                     var session = sessionFactory.withOptions().connection(connection).openSession()) {
                    session.beginTransaction();
                    var now = OffsetDateTime.now();
                    var department = new OrgChart(); department.setName("Phase 1 Test Sales");
                    department.setType(OrgChartType.D); department.setCreatedAt(now); department.setUpdatedAt(now);
                    session.persist(department);
                    var role = new Role(); role.setName("Phase 1 Test Retail Sales"); role.setOrgChart(department);
                    role.setCreatedAt(now); role.setUpdatedAt(now); role.setDefaultReviewFrequency(ReviewFrequency.QUARTERLY);
                    session.persist(role);
                    var superior = staff("Phase 1 Test Superior", role, now); session.persist(superior);
                    var employee = staff("Phase 1 Test Employee", role, now); employee.setManager(superior);
                    session.persist(employee);

                    var factory = new JpaRepositoryFactory(session);
                    var periods = factory.getRepository(AnnualKpiReviewPeriodRepository.class);
                    var roles = factory.getRepository(ReviewPeriodRoleConfigurationRepository.class);
                    var checkpoints = factory.getRepository(ReviewCheckpointRepository.class);
                    var participants = factory.getRepository(ReviewPeriodParticipantRepository.class);
                    var period = new AnnualKpiReviewPeriod(); period.setName("Phase 1 Test 2027");
                    period.setStartDate(LocalDate.of(2027, 1, 1)); period.setEndDate(LocalDate.of(2027, 12, 31));
                    period.setCompanyKpiWeight(new BigDecimal("15.00"));
                    period.setDepartmentKpiWeight(new BigDecimal("25.00"));
                    period.setIndividualKpiWeight(new BigDecimal("60.00"));
                    period.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.FINAL_CHECKPOINT);
                    period.setSelfAssessmentDaysAfterCheckpoint(5); period.setSuperiorAssessmentDaysAfterSelfDeadline(5);
                    periods.saveAndFlush(period);
                    var roleConfiguration = new ReviewPeriodRoleConfiguration();
                    roleConfiguration.setReviewPeriod(period); roleConfiguration.setRole(role);
                    roleConfiguration.setReviewFrequency(ReviewFrequency.MONTHLY); roles.saveAndFlush(roleConfiguration);
                    checkpoints.saveAllAndFlush(new ReviewCheckpointGenerator().generate(period, ReviewFrequency.MONTHLY));
                    var participant = new ReviewPeriodParticipantFactory().snapshot(period, employee, roleConfiguration, department);
                    participants.saveAndFlush(participant);
                    session.clear();

                    assertThat(periods.existsByName(period.getName())).isTrue();
                    assertThat(periods.findAllByOrderByStartDateDescIdDesc()).hasSize(1);
                    assertThat(roles.findByReviewPeriodIdAndRoleId(period.getId(), role.getId())).isPresent();
                    assertThat(checkpoints.findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(
                            period.getId(), ReviewFrequency.MONTHLY)).hasSize(12);
                    assertThat(participants.findByReviewPeriodIdAndStaffId(period.getId(), employee.getId()))
                            .get().extracting(ReviewPeriodParticipant::getRoleName).isEqualTo("Phase 1 Test Retail Sales");
                    assertThat(participants.findAllByReviewPeriodIdAndSuperiorId(period.getId(), superior.getId())).hasSize(1);
                    assertThat(participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(employee.getId())).hasSize(1);
                    assertThat(periods.existsOverlappingPeriod(period.getStartDate(), period.getEndDate(), null,
                            List.of(AnnualKpiReviewPeriodStatus.UPCOMING, AnnualKpiReviewPeriodStatus.OPEN))).isFalse();
                    execute(connection, "UPDATE annual_kpi_review_period SET status='OPEN' WHERE id=" + period.getId());
                    assertThat(periods.existsOverlappingPeriod(period.getStartDate(), period.getEndDate(), null,
                            List.of(AnnualKpiReviewPeriodStatus.UPCOMING, AnnualKpiReviewPeriodStatus.OPEN))).isTrue();
                    assertThat(periods.existsOverlappingPeriod(period.getStartDate(), period.getEndDate(), period.getId(),
                            List.of(AnnualKpiReviewPeriodStatus.OPEN))).isFalse();

                    verifyApplicationWorkflow(factory, connection, periods, roles, checkpoints, participants,
                            role, superior, clock(), env);

                    reject(connection, "23505", "INSERT INTO review_period_participant(review_period_id,staff_id,review_frequency) "
                            + "SELECT review_period_id,staff_id,review_frequency FROM review_period_participant");
                    reject(connection, "23505", "INSERT INTO review_period_role_configuration(review_period_id,role_id,review_frequency) "
                            + "SELECT review_period_id,role_id,review_frequency FROM review_period_role_configuration");
                    reject(connection, "23505", "INSERT INTO review_checkpoint(review_period_id,review_frequency,sequence_number,"
                            + "start_date,end_date,self_assessment_deadline,superior_assessment_deadline) SELECT review_period_id,"
                            + "review_frequency,sequence_number,start_date,end_date,self_assessment_deadline,superior_assessment_deadline FROM review_checkpoint");
                    reject(connection, "23514", "UPDATE annual_kpi_review_period SET individual_kpi_weight=59");
                    reject(connection, "23514", "UPDATE review_checkpoint SET self_assessment_deadline=end_date");
                    reject(connection, "23514", "UPDATE role SET default_review_frequency='WEEKLY'");
                    reject(connection, "23503", "DELETE FROM staff WHERE id='" + employee.getId() + "'");
                    session.getTransaction().rollback();
                }
            } finally {
                connection.rollback();
            }
            assertThat(count(connection, "public.staff")).isEqualTo(staffCount);
            assertThat(count(connection, "public.role")).isEqualTo(roleCount);
            assertThat(count(connection, "public.flyway_schema_history")).isEqualTo(historyCount);
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT count(*) FROM information_schema.schemata WHERE schema_name='" + schema + "'")) {
                rows.next(); assertThat(rows.getInt(1)).isZero();
            }
            connection.rollback();
        }
    }

    private Clock clock() {
        return Clock.fixed(Instant.parse("2026-10-06T00:00:00Z"), ZoneId.of("Asia/Kuala_Lumpur"));
    }

    private void verifyApplicationWorkflow(JpaRepositoryFactory factory, Connection connection,
            AnnualKpiReviewPeriodRepository periods, ReviewPeriodRoleConfigurationRepository configurations,
            ReviewCheckpointRepository checkpoints, ReviewPeriodParticipantRepository participants,
            Role role, Staff actor, Clock clock, Dotenv env) throws Exception {
        var service = new AnnualKpiReviewPeriodServiceImpl(periods, configurations, checkpoints, participants,
                factory.getRepository(RoleRepository.class), Mappers.getMapper(AnnualKpiReviewPeriodMapper.class),
                new AnnualReviewPeriodConfigurationValidator(), new ReviewCheckpointGenerator(), clock);
        var request = AnnualKpiReviewPeriodServiceImplTest.validRequest();
        request.setName("Application workflow 2028");
        request.setStartDate(LocalDate.of(2028, 1, 1));
        request.setEndDate(LocalDate.of(2028, 12, 31));
        request.setCompanyKpiCreationDeadline(request.getStartDate());
        request.setDepartmentKpiCreationDeadline(request.getStartDate());
        request.setIndividualKpiSubmissionDeadline(request.getStartDate());
        request.setIndividualKpiApprovalDeadline(request.getStartDate());
        request.setAttitudeSelfAssessmentDeadline(LocalDate.of(2028, 12, 5));
        request.setSuperiorAttitudeEvaluationDeadline(LocalDate.of(2028, 12, 15));
        request.setAppraisalRecommendationDeadline(LocalDate.of(2029, 1, 15));
        request.setHrFinalisationDeadline(LocalDate.of(2029, 1, 25));
        request.setRoleConfigurations(List.of(AnnualKpiReviewPeriodServiceImplTest.frequency(role.getId(), ReviewFrequency.MONTHLY)));

        long initial = count(connection, "annual_kpi_review_period");
        assertThat(service.preview(request, null).getCheckpoints()).hasSize(12);
        assertThat(count(connection, "annual_kpi_review_period")).isEqualTo(initial);
        var draft = service.create(request, false, actor.getId());
        assertThat(draft.getCreatedAt()).isNotNull();
        assertThat(draft.getCreatedBy()).isEqualTo(actor.getId());
        assertThat(draft.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.DRAFT);
        assertThat(draft.getCheckpoints()).isEmpty();
        var published = service.publish(draft.getId(), actor.getId());
        assertThat(published.getStatus()).isEqualTo(AnnualKpiReviewPeriodStatus.UPCOMING);
        assertThat(published.getCheckpoints()).hasSize(12);
        assertThat(published.getCheckpoints().get(11).getSuperiorAssessmentDeadline())
                .isEqualTo(LocalDate.of(2029, 1, 10));

        try (var competing = DriverManager.getConnection(env.get("DB_URL"), env.get("DB_USER"), env.get("DB_PASS"))) {
            competing.setAutoCommit(false);
            try (var statement = competing.createStatement();
                 var result = statement.executeQuery("SELECT pg_try_advisory_xact_lock(20261006, 1)")) {
                result.next();
                assertThat(result.getBoolean(1)).as("configuration lock is held until the writer transaction ends").isFalse();
            } finally {
                competing.rollback();
            }
        }
        var overlapping = AnnualKpiReviewPeriodServiceImplTest.validRequest();
        overlapping.setName("Overlapping 2027");
        overlapping.setRoleConfigurations(request.getRoleConfigurations());
        assertThatThrownBy(() -> service.create(overlapping, true, actor.getId()))
                .hasMessageContaining("overlap");
        assertThat(count(connection, "annual_kpi_review_period")).isEqualTo(initial + 1);

        request.getRoleConfigurations().get(0).setReviewFrequency(ReviewFrequency.QUARTERLY);
        var updated = service.update(draft.getId(), request, actor.getId());
        assertThat(updated.getCheckpoints()).hasSize(4);
        assertThat(service.get(draft.getId()).getRoleConfigurations().get(0).getReviewFrequency())
                .isEqualTo(ReviewFrequency.QUARTERLY);
        assertThat(service.list()).hasSize(2);
        assertThat(service.availableRoles()).isNotEmpty();
        service.delete(draft.getId());
        assertThat(count(connection, "annual_kpi_review_period")).isEqualTo(initial);
        assertThat(configurations.findAllByReviewPeriodIdOrderByIdAsc(draft.getId())).isEmpty();
        assertThat(checkpoints.findAllByReviewPeriodIdOrderByReviewFrequencyAscSequenceNumberAsc(draft.getId())).isEmpty();
    }

    private Staff staff(String name, Role role, OffsetDateTime now) {
        var staff = new Staff(); staff.setId(UUID.randomUUID()); staff.setName(name);
        staff.setEmail(UUID.randomUUID() + "@phase1.invalid"); staff.setRole(role);
        staff.setAccountStatus(StaffAccountStatus.ACTIVE); staff.setCreatedAt(now); staff.setUpdatedAt(now);
        return staff;
    }

    private void reject(Connection connection, String state, String sql) throws SQLException {
        var savepoint = connection.setSavepoint();
        try {
            var failure = assertThrows(SQLException.class, () -> execute(connection, sql));
            assertThat(failure.getSQLState()).isEqualTo(state);
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private long count(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rows.next(); return rows.getLong(1);
        }
    }
}
