package com.tbm.careerpathlearning.service.impl;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "APEX_PHASE1_POSTGRES_TEST", matches = "true")
class ReviewPeriodRefinementMigrationPostgresTest {
    @Test
    void preservesDeadlineHistoryAndEmployeeSettingsWhileExcludingSystemRoles() throws Exception {
        fixture(c -> {
            period(c, 1);
            period(c, 2);
            sql(c, "UPDATE annual_kpi_review_period SET company_kpi_creation_deadline='2026-12-01',"
                    + "department_kpi_creation_deadline='2026-12-05',individual_kpi_submission_deadline='2026-12-10',"
                    + "individual_kpi_approval_deadline='2026-12-15' WHERE id=1");
            sql(c, "UPDATE annual_kpi_review_period SET status='UPCOMING' WHERE id=1");
            sql(c, "INSERT INTO review_period_role_configuration(review_period_id,role_id,review_frequency) "
                    + "VALUES(1,1,'ANNUALLY'),(1,2,'MONTHLY'),(2,1,'MONTHLY'),(2,2,'MONTHLY')");
            for (int id : new int[]{1, 2}) {
                checkpoint(c, id, "MONTHLY");
            }
            checkpoint(c, 1, "ANNUALLY");
            migrate(c);
            assertThat(number(c, "SELECT count(*) FROM role WHERE id=1 AND NOT performance_review_eligible")).isEqualTo(1);
            assertThat(number(c, "SELECT count(*) FROM role WHERE id=2 AND performance_review_eligible")).isEqualTo(1);
            assertThat(number(c, "SELECT count(*) FROM review_period_role_configuration WHERE role_id=1")).isZero();
            assertThat(number(c, "SELECT count(*) FROM review_period_role_configuration WHERE role_id=2")).isEqualTo(2);
            assertThat(number(c, "SELECT count(*) FROM review_checkpoint WHERE review_frequency='ANNUALLY'")).isZero();
            assertThat(number(c, "SELECT count(*) FROM review_checkpoint WHERE review_frequency='MONTHLY'")).isEqualTo(2);
            assertThat(number(c, "SELECT count(*) FROM annual_kpi_review_period WHERE id=1 "
                    + "AND kpi_setup_deadline='2026-12-15' AND company_kpi_creation_deadline='2026-12-01' "
                    + "AND individual_kpi_approval_deadline='2026-12-15' AND status='UPCOMING'")).isEqualTo(1);
            assertThat(number(c, "SELECT count(*) FROM annual_kpi_review_period WHERE id=2 AND kpi_setup_deadline IS NULL")).isEqualTo(1);
            reject(c, "23514", "UPDATE annual_kpi_review_period SET kpi_setup_deadline='2027-01-02' WHERE id=1");
            reject(c, "23514", "UPDATE annual_kpi_review_period SET kpi_setup_deadline=NULL WHERE id=1");
            sql(c, "UPDATE annual_kpi_review_period SET kpi_setup_deadline='2026-12-20' WHERE id=1");
            assertThat(number(c, "SELECT count(*) FROM annual_kpi_review_period WHERE id=1 AND individual_kpi_approval_deadline='2026-12-15'")).isEqualTo(1);
        });
    }

    @Test
    void reconcilesOnlyTheVerifiedPlainHibernateColumnWithoutLosingSavedValues() throws Exception {
        fixture(c -> {
            period(c, 1);
            sql(c, "ALTER TABLE annual_kpi_review_period ADD COLUMN kpi_setup_deadline DATE");
            sql(c, "UPDATE annual_kpi_review_period SET kpi_setup_deadline='2026-12-20',individual_kpi_approval_deadline='2026-12-15' WHERE id=1");
            migrate(c);
            assertThat(number(c, "SELECT count(*) FROM annual_kpi_review_period WHERE kpi_setup_deadline='2026-12-20' "
                    + "AND individual_kpi_approval_deadline='2026-12-15'")).isEqualTo(1);
        });
    }

    @Test
    void refusesIncompatiblePreexistingDeadlineColumn() throws Exception {
        fixture(c -> {
            sql(c, "ALTER TABLE annual_kpi_review_period ADD COLUMN kpi_setup_deadline VARCHAR(30)");
            var point = c.setSavepoint();
            assertThat(assertThrows(SQLException.class, () -> migrate(c)).getMessage()).contains("unexpected existing");
            c.rollback(point);
        });
    }

    @Test
    void refusesToEraseHistoricalSuperAdminParticipants() throws Exception {
        fixture(c -> {
            period(c, 1);
            sql(c, "INSERT INTO staff(id,role_id) VALUES('00000000-0000-0000-0000-000000000001',1)");
            long level = number(c, "SELECT id FROM review_period_employee_level_configuration WHERE review_period_id=1 AND employee_level_id=1");
            sql(c, "INSERT INTO review_period_participant(review_period_id,staff_id,role_id,review_frequency,employee_level_configuration_id) "
                    + "VALUES(1,'00000000-0000-0000-0000-000000000001',1,'ANNUALLY'," + level + ")");
            var point = c.setSavepoint();
            assertThat(assertThrows(SQLException.class, () -> migrate(c)).getMessage()).contains("manual review");
            c.rollback(point);
            assertThat(number(c, "SELECT count(*) FROM review_period_participant")).isEqualTo(1);
            assertThat(number(c, "SELECT count(*) FROM information_schema.columns WHERE table_schema=current_schema() "
                    + "AND column_name='performance_review_eligible'")).isZero();
        });
    }

    @Test
    void incompletePublishedDeadlineRequiresReviewedBackfill() throws Exception {
        fixture(c -> {
            period(c, 1);
            sql(c, "UPDATE annual_kpi_review_period SET status='UPCOMING' WHERE id=1");
            var point = c.setSavepoint();
            assertThat(assertThrows(SQLException.class, () -> migrate(c)).getSQLState()).isEqualTo("23514");
            c.rollback(point);
            assertThat(number(c, "SELECT count(*) FROM annual_kpi_review_period")).isEqualTo(1);
        });
    }

    private void fixture(Scenario scenario) throws Exception {
        var e = Dotenv.configure().directory(".").load();
        assertThat(URI.create(e.get("DB_URL").substring(5)).getHost()).isIn("localhost", "127.0.0.1", "::1");
        try (var c = DriverManager.getConnection(e.get("DB_URL"), e.get("DB_USER"), e.get("DB_PASS"))) {
            c.setAutoCommit(false);
            String schema = "apex_v30_test_" + UUID.randomUUID().toString().replace("-", "");
            long history = number(c, "SELECT count(*) FROM public.flyway_schema_history");
            try {
                sql(c, "CREATE SCHEMA " + schema);
                sql(c, "SET LOCAL search_path TO " + schema);
                sql(c, "CREATE TABLE role(id BIGINT PRIMARY KEY,name VARCHAR(255))");
                sql(c, "CREATE TABLE staff(id UUID PRIMARY KEY,role_id BIGINT REFERENCES role(id))");
                sql(c, "CREATE TABLE org_chart(id BIGINT PRIMARY KEY)");
                ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/annual-kpi/V27__annual_review_period_foundation.sql"));
                sql(c, new ClassPathResource("db/migration/annual-kpi/V29__employee_level_kpi_weightages.sql").getContentAsString(StandardCharsets.UTF_8));
                sql(c, "INSERT INTO role(id,name) VALUES(1,'superadmin'),(2,'Retail Sales')");
                scenario.run(c);
            } finally { c.rollback(); }
            assertThat(number(c, "SELECT count(*) FROM public.flyway_schema_history")).isEqualTo(history);
            assertThat(number(c, "SELECT count(*) FROM information_schema.schemata WHERE schema_name='" + schema + "'")).isZero();
            c.rollback();
        }
    }

    private void period(Connection c, int id) throws SQLException {
        sql(c, "INSERT INTO annual_kpi_review_period(id,reference_number,name,start_date,end_date,annual_kpi_consolidation_method,"
                + "self_assessment_days_after_checkpoint,superior_assessment_days_after_self_deadline) VALUES(" + id
                + ",'" + UUID.randomUUID() + "','Period " + id + "','2027-01-01','2027-12-31','FINAL_CHECKPOINT',5,5)");
        sql(c, "INSERT INTO review_period_employee_level_configuration(review_period_id,employee_level_id,company_kpi_weight,"
                + "department_kpi_weight,individual_kpi_weight) SELECT " + id + ",id,default_company_kpi_weight,"
                + "default_department_kpi_weight,default_individual_kpi_weight FROM employee_level");
    }

    private void checkpoint(Connection c, int period, String frequency) throws SQLException {
        sql(c, "INSERT INTO review_checkpoint(review_period_id,review_frequency,sequence_number,start_date,end_date,"
                + "self_assessment_deadline,superior_assessment_deadline) VALUES(" + period + ",'" + frequency
                + "',1,'2027-01-01','2027-12-31','2028-01-05','2028-01-10')");
    }
    private void migrate(Connection c) throws Exception {
        sql(c, new ClassPathResource("db/migration/annual-kpi/V30__review_period_eligibility_and_setup_deadline.sql").getContentAsString(StandardCharsets.UTF_8));
    }
    private void sql(Connection c, String query) throws SQLException {
        try (var s = c.createStatement()) { s.execute(query); }
    }
    private long number(Connection c, String query) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery(query)) { r.next(); return r.getLong(1); }
    }
    private void reject(Connection c, String state, String query) throws Exception {
        var point = c.setSavepoint();
        try { assertThat(assertThrows(SQLException.class, () -> sql(c, query)).getSQLState()).isEqualTo(state); }
        finally { c.rollback(point); c.releaseSavepoint(point); }
    }
    @FunctionalInterface private interface Scenario { void run(Connection c) throws Exception; }
}
