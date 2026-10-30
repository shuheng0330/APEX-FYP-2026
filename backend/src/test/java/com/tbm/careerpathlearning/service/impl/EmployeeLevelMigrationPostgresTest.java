package com.tbm.careerpathlearning.service.impl;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class EmployeeLevelMigrationPostgresTest {
    @Test
    void emptySchemaSeedsSixLevelsWithoutClassifyingExistingRoles() throws Exception {
        fixture(c -> {
            sql(c, "INSERT INTO role(id,name) VALUES(1,'Manager')");
            migrate(c);
            assertThat(number(c, "SELECT count(*) FROM employee_level")).isEqualTo(6);
            assertThat(number(c, "SELECT count(*) FROM role WHERE employee_level_id IS NOT NULL")).isZero();
            assertThat(number(c, "SELECT default_company_kpi_weight FROM employee_level WHERE code='EXECUTIVE'")).isEqualTo(15);
            assertThat(number(c, "SELECT count(*) FROM employee_level WHERE default_company_kpi_weight "
                    + "+ default_department_kpi_weight + default_individual_kpi_weight=100")).isEqualTo(6);
            reject(c, "23514", "UPDATE employee_level SET default_individual_kpi_weight=99");
        });
    }

    @Test
    void migrationPreservesActualHistoricalWeightsAndIncompleteDraftValues() throws Exception {
        fixture(c -> {
            insertPeriod(c, 1, "DRAFT", "23", "31", "46");
            insertPeriod(c, 2, "CLOSED", "17", "28", "55");
            sql(c, "UPDATE annual_kpi_review_period SET company_kpi_weight=NULL WHERE id=1");
            migrate(c);
            assertThat(number(c, "SELECT count(*) FROM review_period_employee_level_configuration WHERE "
                    + "review_period_id=1 AND company_kpi_weight IS NULL AND department_kpi_weight=31 AND individual_kpi_weight=46")).isEqualTo(6);
            assertThat(number(c, "SELECT count(*) FROM review_period_employee_level_configuration WHERE "
                    + "review_period_id=2 AND company_kpi_weight=17 AND department_kpi_weight=28 AND individual_kpi_weight=55")).isEqualTo(6);
            assertThat(number(c, "SELECT individual_kpi_weight FROM annual_kpi_review_period WHERE id=2")).isEqualTo(55);
            reject(c, "23505", "INSERT INTO review_period_employee_level_configuration(review_period_id,employee_level_id) VALUES(1,1)");
            reject(c, "23514", "UPDATE review_period_employee_level_configuration SET company_kpi_weight=-1");
            sql(c, "INSERT INTO role(id,name) VALUES(1,'Example')");
            long first = number(c, "SELECT id FROM review_period_employee_level_configuration WHERE review_period_id=1 AND employee_level_id=1");
            sql(c, "INSERT INTO review_period_role_configuration(review_period_id,role_id,review_frequency,employee_level_configuration_id) "
                    + "VALUES(1,1,'MONTHLY'," + first + ")");
            long second = number(c, "SELECT id FROM review_period_employee_level_configuration WHERE review_period_id=2 AND employee_level_id=1");
            reject(c, "23503", "UPDATE review_period_role_configuration SET employee_level_configuration_id=" + second);
            reject(c, "23503", "DELETE FROM review_period_employee_level_configuration WHERE id=" + first);
            sql(c, "INSERT INTO staff(id) VALUES('00000000-0000-0000-0000-000000000001')");
            sql(c, "INSERT INTO review_period_participant(review_period_id,staff_id,review_frequency,employee_level_configuration_id) "
                    + "VALUES(1,'00000000-0000-0000-0000-000000000001','MONTHLY'," + first + ")");
            reject(c, "23503", "UPDATE review_period_participant SET employee_level_configuration_id=" + second);
            reject(c, "23502", "UPDATE review_period_participant SET employee_level_configuration_id=NULL");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"role", "participant"})
    void refusesAmbiguousHistoricalAssociationsBeforeCreatingNewTables(String record) throws Exception {
        fixture(c -> {
            insertPeriod(c, 1, "OPEN", "15", "25", "60");
            sql(c, "INSERT INTO role(id,name) VALUES(1,'Manager')");
            sql(c, "INSERT INTO staff(id) VALUES('00000000-0000-0000-0000-000000000001')");
            if (record.equals("role")) {
                sql(c, "INSERT INTO review_period_role_configuration(review_period_id,role_id,review_frequency) VALUES(1,1,'MONTHLY')");
            } else {
                sql(c, "INSERT INTO review_period_participant(review_period_id,staff_id,review_frequency) "
                        + "VALUES(1,'00000000-0000-0000-0000-000000000001','MONTHLY')");
            }
            var savepoint = c.setSavepoint();
            var error = assertThrows(SQLException.class, () -> migrate(c));
            assertThat(error.getMessage()).contains("explicit reviewed historical Employee Level mapping");
            c.rollback(savepoint);
            assertThat(number(c, "SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() "
                    + "AND table_name='employee_level'")).isZero();
            assertThat(number(c, "SELECT count(*) FROM annual_kpi_review_period")).isEqualTo(1);
        });
    }

    private void fixture(Scenario scenario) throws Exception {
        var e = Dotenv.configure().directory(".").load();
        assertThat(URI.create(e.get("DB_URL").substring(5)).getHost()).isIn("localhost", "127.0.0.1", "::1");
        try (var c = DriverManager.getConnection(e.get("DB_URL"), e.get("DB_USER"), e.get("DB_PASS"))) {
            c.setAutoCommit(false);
            String schema = "apex_v29_test_" + UUID.randomUUID().toString().replace("-", "");
            long history = number(c, "SELECT count(*) FROM public.flyway_schema_history");
            try {
                sql(c, "CREATE SCHEMA " + schema);
                sql(c, "SET LOCAL search_path TO " + schema);
                sql(c, "CREATE TABLE role(id BIGINT PRIMARY KEY,name VARCHAR(255))");
                sql(c, "CREATE TABLE staff(id UUID PRIMARY KEY)");
                sql(c, "CREATE TABLE org_chart(id BIGINT PRIMARY KEY)");
                ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/annual-kpi/V27__annual_review_period_foundation.sql"));
                scenario.run(c);
            } finally { c.rollback(); }
            assertThat(number(c, "SELECT count(*) FROM public.flyway_schema_history")).isEqualTo(history);
            assertThat(number(c, "SELECT count(*) FROM information_schema.schemata WHERE schema_name='" + schema + "'")).isZero();
            c.rollback();
        }
    }

    private void insertPeriod(Connection c, int id, String status, String company, String department, String individual) throws Exception {
        sql(c, "INSERT INTO annual_kpi_review_period(id,reference_number,name,start_date,end_date,status,company_kpi_weight,"
                + "department_kpi_weight,individual_kpi_weight,annual_kpi_consolidation_method,self_assessment_days_after_checkpoint,"
                + "superior_assessment_days_after_self_deadline) VALUES(" + id + ",'" + UUID.randomUUID() + "','Period " + id
                + "','2027-01-01','2027-12-31','" + status + "'," + company + "," + department + "," + individual
                + ",'FINAL_CHECKPOINT',5,5)");
    }
    private void migrate(Connection c) throws Exception {
        sql(c, new ClassPathResource("db/migration/annual-kpi/V29__employee_level_kpi_weightages.sql").getContentAsString(StandardCharsets.UTF_8));
    }
    private void sql(Connection c, String query) throws SQLException {
        try (var s = c.createStatement()) { s.execute(query); }
    }
    private long number(Connection c, String query) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery(query)) { r.next(); return r.getLong(1); }
    }
    private void reject(Connection c, String state, String query) throws Exception {
        var savepoint = c.setSavepoint();
        try { assertThat(assertThrows(SQLException.class, () -> sql(c, query)).getSQLState()).isEqualTo(state); }
        finally { c.rollback(savepoint); c.releaseSavepoint(savepoint); }
    }
    @FunctionalInterface private interface Scenario { void run(Connection c) throws Exception; }
}
