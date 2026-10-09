package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.AuthorityName;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "APEX_PHASE1_POSTGRES_TEST", matches = "true")
class AnnualReviewPeriodPermissionPostgresTest {
    private static final String NAME = AuthorityName.CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD.name();

    @Test
    void migrationAddsOnlyAnnualPermissionAndSuperadminGrantAndCanBeRetried() throws Exception {
        inIsolatedSchema(connection -> {
            String authorities = snapshot(connection, "authority", "id");
            String grants = snapshot(connection, "role_authority", "role_id,authority_id");
            migrate(connection);
            assertThat(scalar(connection, "SELECT count(*) FROM authority WHERE name='" + NAME + "'" )).isEqualTo(1);
            assertThat(scalar(connection, "SELECT count(*) FROM role_authority ra JOIN authority a ON a.id=ra.authority_id "
                    + "JOIN role r ON r.id=ra.role_id WHERE a.name='" + NAME
                    + "' AND lower(trim(r.name))='superadmin' AND NOT r.is_deleted")).isEqualTo(1);
            assertThat(scalar(connection, "SELECT count(*) FROM role_authority ra JOIN authority a ON a.id=ra.authority_id "
                    + "JOIN role r ON r.id=ra.role_id WHERE a.name='" + NAME
                    + "' AND lower(trim(r.name))<>'superadmin'")).isZero();
            assertThat(snapshot(connection, "authority WHERE name<>'" + NAME + "'", "id")).isEqualTo(authorities);
            assertThat(snapshot(connection, "role_authority WHERE authority_id NOT IN (SELECT id FROM authority WHERE name='"
                    + NAME + "')", "role_id,authority_id")).isEqualTo(grants);
            assertThat(text(connection, "SELECT label_key FROM authority WHERE name='" + NAME + "'"))
                    .isEqualTo("auth.can.manage.annual.kpi.review.period");
            assertThat(text(connection, "SELECT description_key FROM authority WHERE name='" + NAME + "'"))
                    .isEqualTo("auth.can.manage.annual.kpi.review.period.desc");
            String migratedAuthorities = snapshot(connection, "authority", "id");
            String migratedGrants = snapshot(connection, "role_authority", "role_id,authority_id");
            migrate(connection);
            assertThat(snapshot(connection, "authority", "id")).isEqualTo(migratedAuthorities);
            assertThat(snapshot(connection, "role_authority", "role_id,authority_id")).isEqualTo(migratedGrants);
            var savepoint = connection.setSavepoint();
            assertThatThrownBy(() -> execute(connection,
                    "INSERT INTO authority(name,label_key,description_key) VALUES ('INVALID_PERMISSION','x','x')"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("authority_name_check");
            connection.rollback(savepoint);
        });
    }

    @Test
    void missingSuperadminFailsWithoutInstallingPermission() throws Exception {
        inIsolatedSchema(connection -> {
            execute(connection, "UPDATE role SET is_deleted=true WHERE lower(trim(name))='superadmin'");
            expectRoleFailure(connection);
        });
    }

    @Test
    void ambiguousSuperadminFailsWithoutInstallingPermission() throws Exception {
        inIsolatedSchema(connection -> {
            execute(connection, "UPDATE role SET name='superadmin', is_deleted=false WHERE id="
                    + "(SELECT min(id) FROM role WHERE lower(trim(name))<>'superadmin')");
            expectRoleFailure(connection);
        });
    }

    private void expectRoleFailure(Connection connection) throws Exception {
        String before = snapshot(connection, "authority", "id");
        var savepoint = connection.setSavepoint();
        assertThatThrownBy(() -> migrate(connection)).isInstanceOf(SQLException.class)
                .hasMessageContaining("Expected one active superadmin role");
        connection.rollback(savepoint);
        assertThat(snapshot(connection, "authority", "id")).isEqualTo(before);
        assertThat(scalar(connection, "SELECT count(*) FROM authority WHERE name='" + NAME + "'" )).isZero();
    }

    private void inIsolatedSchema(Scenario scenario) throws Exception {
        var env = Dotenv.configure().directory(".").load();
        String url = env.get("DB_URL");
        assertThat(URI.create(url.substring(5)).getHost()).isIn("localhost", "127.0.0.1", "::1");
        String schema = "apex_permission_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(url, env.get("DB_USER"), env.get("DB_PASS"))) {
            connection.setAutoCommit(false);
            String publicAuthorities = snapshot(connection, "public.authority", "id");
            String publicGrants = snapshot(connection, "public.role_authority", "role_id,authority_id");
            String publicHistory = snapshot(connection, "public.flyway_schema_history", "installed_rank");
            try {
                execute(connection, "CREATE SCHEMA " + schema);
                execute(connection, "SET LOCAL search_path TO " + schema);
                for (String table : List.of("role", "authority", "role_authority")) {
                    execute(connection, "CREATE TABLE " + table + " (LIKE public." + table + " INCLUDING ALL)");
                }
                execute(connection, "INSERT INTO role OVERRIDING SYSTEM VALUE SELECT * FROM public.role");
                // Reconstruct V28's input, excluding authorities introduced by later migrations.
                execute(connection, "INSERT INTO authority OVERRIDING SYSTEM VALUE SELECT * FROM public.authority "
                        + "WHERE name NOT IN ('" + NAME + "','CAN_MANAGE_COMPANY_KPI','CAN_MANAGE_DEPARTMENT_KPI','CAN_APPROVE_DEPARTMENT_KPI',"
                        + "'CAN_REVIEW_INDIVIDUAL_KPI','CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE','CAN_REVIEW_KPI_ASSESSMENT','CAN_MANAGE_ATTITUDE_CONFIGURATION')");
                execute(connection, "INSERT INTO role_authority SELECT ra.* FROM public.role_authority ra "
                        + "JOIN authority a ON a.id=ra.authority_id");
                execute(connection, "SELECT setval(pg_get_serial_sequence('authority','id'), (SELECT max(id)+1 FROM authority), false)");
                execute(connection, "ALTER TABLE authority DROP CONSTRAINT IF EXISTS authority_name_check");
                execute(connection, "ALTER TABLE authority ADD CONSTRAINT authority_name_check CHECK (name<>'" + NAME + "')");
                execute(connection, "ALTER TABLE role_authority ADD FOREIGN KEY (role_id) REFERENCES role(id)");
                execute(connection, "ALTER TABLE role_authority ADD FOREIGN KEY (authority_id) REFERENCES authority(id)");
                scenario.run(connection);
            } finally {
                connection.rollback();
            }
            assertThat(snapshot(connection, "public.authority", "id")).isEqualTo(publicAuthorities);
            assertThat(snapshot(connection, "public.role_authority", "role_id,authority_id")).isEqualTo(publicGrants);
            assertThat(snapshot(connection, "public.flyway_schema_history", "installed_rank")).isEqualTo(publicHistory);
            assertThat(scalar(connection, "SELECT count(*) FROM information_schema.schemata WHERE schema_name='" + schema + "'" )).isZero();
            connection.rollback();
        }
    }

    private void migrate(Connection connection) throws Exception {
        execute(connection, new ClassPathResource("db/migration/annual-kpi/V28__annual_kpi_review_period_permission.sql")
                .getContentAsString(StandardCharsets.UTF_8));
    }

    private static String snapshot(Connection connection, String source, String order) throws SQLException {
        return text(connection, "SELECT coalesce(json_agg(t)::text,'[]') FROM (SELECT * FROM " + source
                + " ORDER BY " + order + ") t");
    }

    private static String text(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getString(1);
        }
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        return Long.parseLong(text(connection, sql));
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }

    @FunctionalInterface
    private interface Scenario { void run(Connection connection) throws Exception; }
}
