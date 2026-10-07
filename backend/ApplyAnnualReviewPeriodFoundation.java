import io.github.cdimascio.dotenv.Dotenv;
import org.flywaydb.core.Flyway;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Explicit local-only migration command; it never starts Spring or discovers legacy migrations. */
public class ApplyAnnualReviewPeriodFoundation {
    private static final List<String> TABLES = List.of("annual_kpi_review_period",
            "review_period_role_configuration", "review_checkpoint", "review_period_participant");

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !"--apply".equals(args[0])) {
            throw new IllegalArgumentException("Usage: ApplyAnnualReviewPeriodFoundation.java --apply <pg_dump executable>");
        }
        var environment = Dotenv.configure().directory(".").load();
        String url = environment.get("DB_URL");
        String user = environment.get("DB_USER");
        String password = environment.get("DB_PASS");
        var target = URI.create(url.substring(5));
        if (!List.of("localhost", "127.0.0.1", "::1").contains(target.getHost()) || target.getQuery() != null) {
            throw new IllegalArgumentException("This runner supports only a plain local PostgreSQL JDBC URL");
        }
        if (!Files.isRegularFile(Path.of(args[1]))) throw new IllegalArgumentException("pg_dump executable not found");
        if (!Files.isRegularFile(Path.of("src/main/resources/db/migration/annual-kpi/V27__annual_review_period_foundation.sql"))) {
            throw new IllegalStateException("Run from the backend directory; the Phase 1 migration file is missing");
        }
        boolean alreadyApplied;
        boolean permissionAlreadyApplied;
        boolean levelsAlreadyApplied;
        boolean refinementAlreadyApplied;
        long staffCount;
        long roleCount;
        try (var connection = DriverManager.getConnection(url, user, password)) {
            connection.setAutoCommit(false);
            connection.setReadOnly(true);
            if (scalar(connection, "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND "
                    + "((table_name='staff' AND column_name='id' AND udt_name='uuid') OR "
                    + "(table_name IN ('role','org_chart') AND column_name='id' AND udt_name='int8'))") != 3) {
                throw new IllegalStateException("Inherited staff/role/organisation ID types do not match Phase 1");
            }
            if (scalar(connection, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    + "AND table_name='flyway_schema_history'") != 1) {
                throw new IllegalStateException("Expected inherited Flyway history table is missing; assess this database first");
            }
            long historyCount = scalar(connection, "SELECT count(*) FROM public.flyway_schema_history");
            alreadyApplied = scalar(connection, "SELECT count(*) FROM public.flyway_schema_history "
                    + "WHERE version='27' AND type='SQL' AND success") == 1;
            permissionAlreadyApplied = scalar(connection, "SELECT count(*) FROM public.flyway_schema_history "
                    + "WHERE version='28' AND type='SQL' AND success") == 1;
            levelsAlreadyApplied = scalar(connection, "SELECT count(*) FROM public.flyway_schema_history "
                    + "WHERE version='29' AND type='SQL' AND success") == 1;
            refinementAlreadyApplied = scalar(connection, "SELECT count(*) FROM public.flyway_schema_history "
                    + "WHERE version='30' AND type='SQL' AND success") == 1;
            if (historyCount != 0 && (scalar(connection, "SELECT count(*) FROM public.flyway_schema_history "
                    + "WHERE version='26' AND type='BASELINE' AND success") != 1
                    || (permissionAlreadyApplied && !alreadyApplied)
                    || (levelsAlreadyApplied && !permissionAlreadyApplied)
                    || (refinementAlreadyApplied && !levelsAlreadyApplied)
                    || historyCount != 1 + (alreadyApplied ? 1 : 0) + (permissionAlreadyApplied ? 1 : 0) + (levelsAlreadyApplied ? 1 : 0) + (refinementAlreadyApplied ? 1 : 0))) {
                throw new IllegalStateException("Unexpected migration history; no baselining or migration will be attempted");
            }
            long tableCount = scalar(connection, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    + "AND table_name IN ('" + String.join("','", TABLES) + "')");
            long frequencyColumnCount = scalar(connection, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_schema='public' AND table_name='role' AND column_name='default_review_frequency'");
            if (tableCount != (alreadyApplied ? 4 : 0) || frequencyColumnCount != (alreadyApplied ? 1 : 0)) {
                throw new IllegalStateException("Partial/untracked Phase 1 schema detected; reconcile it before proceeding");
            }
            long levelTables = scalar(connection, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    + "AND table_name IN ('employee_level','review_period_employee_level_configuration')");
            long levelColumn = scalar(connection, "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='role' AND column_name='employee_level_id'");
            if (levelTables != (levelsAlreadyApplied ? 2 : 0) || levelColumn != (levelsAlreadyApplied ? 1 : 0))
                throw new IllegalStateException("Partial/untracked Employee Level schema detected; reconcile it before proceeding");
            long refinementColumns = scalar(connection, "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND ((table_name='role' AND column_name='performance_review_eligible') "
                    + "OR (table_name='annual_kpi_review_period' AND column_name='kpi_setup_deadline'))");
            boolean plainHibernateDeadline = !refinementAlreadyApplied && refinementColumns == 1
                    && scalar(connection, "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='annual_kpi_review_period' AND column_name='kpi_setup_deadline' "
                    + "AND data_type='date' AND is_nullable='YES' AND column_default IS NULL") == 1;
            if (refinementColumns != (refinementAlreadyApplied ? 2 : 0) && !plainHibernateDeadline)
                throw new IllegalStateException("Partial/untracked V30 schema detected; reconcile it before proceeding");
            staffCount = scalar(connection, "SELECT count(*) FROM public.staff");
            roleCount = scalar(connection, "SELECT count(*) FROM public.role");
            connection.rollback();
        }

        var flyway = Flyway.configure().dataSource(url, user, password)
                .schemas("public").defaultSchema("public")
                .locations("filesystem:src/main/resources/db/migration/annual-kpi")
                .baselineVersion("26").baselineDescription("Inherited Hibernate-managed development schema")
                .baselineOnMigrate(false).outOfOrder(false).cleanDisabled(true).target("30").load();
        if (!refinementAlreadyApplied) {
            var backupDirectory = Path.of(System.getProperty("user.home"), ".apex", "database-backups");
            Files.createDirectories(backupDirectory);
            var backup = backupDirectory.resolve("before-annual-kpi-V30-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".dump");
            var dump = new ProcessBuilder(args[1], "--host=" + target.getHost(),
                    "--port=" + (target.getPort() == -1 ? 5432 : target.getPort()), "--username=" + user,
                    "--dbname=" + target.getPath().substring(1), "--format=custom", "--file=" + backup.toAbsolutePath());
            dump.environment().put("PGPASSWORD", password);
            dump.environment().put("PGCONNECT_TIMEOUT", "5");
            dump.inheritIO();
            if (dump.start().waitFor() != 0 || !Files.isRegularFile(backup) || Files.size(backup) == 0) {
                throw new IllegalStateException("Database backup failed; migration was not started");
            }
            System.out.println("Database backup: " + backup.toAbsolutePath());
            try (var connection = DriverManager.getConnection(url, user, password)) {
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.execute("LOCK TABLE public.flyway_schema_history IN ACCESS EXCLUSIVE MODE");
                    if (scalar(connection, "SELECT count(*) FROM public.flyway_schema_history") == 0) {
                        // Flyway refuses baseline on an existing empty history table. Recreate only this
                        // backed-up, locked, zero-row metadata table; never delete an applied history.
                        statement.execute("DROP TABLE public.flyway_schema_history");
                        connection.commit();
                        flyway.baseline();
                    } else {
                        connection.rollback();
                    }
                }
            }
        }
        var result = flyway.migrate();
        flyway.validate();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            if (scalar(connection, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    + "AND table_name IN ('" + String.join("','", TABLES) + "')") != 4) {
                throw new IllegalStateException("Phase 1 migration did not create all four foundation tables");
            }
            if (scalar(connection, "SELECT count(*) FROM public.staff") != staffCount
                    || scalar(connection, "SELECT count(*) FROM public.role") != roleCount) {
                throw new IllegalStateException("Unexpected inherited row-count change; investigate using the backup");
            }
            if (scalar(connection, "SELECT count(*) FROM public.employee_level") != 6)
                throw new IllegalStateException("Expected six Employee Levels after V30");
            if (scalar(connection, "SELECT count(*) FROM public.role WHERE LOWER(TRIM(name))='superadmin' "
                    + "AND NOT performance_review_eligible") != 1
                    || scalar(connection, "SELECT count(*) FROM public.review_period_role_configuration c "
                    + "JOIN public.role r ON r.id=c.role_id WHERE NOT r.performance_review_eligible") != 0) {
                throw new IllegalStateException("Super Admin exclusion was not established by V30");
            }
        }
        System.out.println("Phase 1 migrations executed: " + result.migrationsExecuted);
        System.out.println("Inherited staff/role counts preserved; global Spring/Flyway/Hibernate settings unchanged.");
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (ResultSet rows = statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
        }
    }
}
