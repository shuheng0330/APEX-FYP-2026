import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Explicit disposable central-results UAT setup. Not loaded by Spring or Flyway. */
public class CentralKpiResultsUat {
    private static final String DATABASE = "apex_central_results_uat";
    private static final String MARKER = "APEX disposable central KPI results UAT clone";
    private static final String PASSWORD = "ApexCentralUat2026!";
    private static final String PERIOD_NAME = "UAT Central Results 2025-2026";
    private static final LocalDate START = LocalDate.of(2025, 10, 1);
    private static final LocalDate END = LocalDate.of(2026, 9, 30);
    private static final Path STATE = Path.of("target", "central-kpi-uat");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> PROTECTED = List.of("annual_kpi_review_period", "review_checkpoint",
            "review_period_participant", "kpi_plan", "kpi", "employee_kpi_assignment", "kpi_assessment");

    private final Dotenv env = Dotenv.configure().directory(".").load();
    private final URI source = URI.create(env.get("DB_URL").substring(5));
    private final String targetUrl = "jdbc:postgresql://" + source.getHost() + ":" + source.getPort() + "/" + DATABASE;
    private final String api;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, String> tokens = new HashMap<>();

    private CentralKpiResultsUat(String api) {
        if (!"jdbc:postgresql".equals(env.get("DB_URL").substring(0, 15))
                || !List.of("localhost", "127.0.0.1").contains(source.getHost())
                || source.getPort() < 1 || source.getQuery() != null
                || source.getPath().equals("/" + DATABASE)) {
            throw new IllegalArgumentException("Expected a distinct plain local development PostgreSQL source");
        }
        URI endpoint = URI.create(api);
        if (!"http".equals(endpoint.getScheme())
                || !List.of("localhost", "127.0.0.1").contains(endpoint.getHost())
                || !endpoint.getPath().isEmpty() || endpoint.getQuery() != null) {
            throw new IllegalArgumentException("Use a local HTTP UAT backend origin");
        }
        this.api = api;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("clone|bootstrap|seed|verify|check-source|check-uat|probe|enable-attitude-setup [local API origin]");
        Files.createDirectories(STATE);
        CentralKpiResultsUat uat = new CentralKpiResultsUat(args.length > 1 ? args[1] : "http://localhost:8082");
        switch (args[0]) {
            case "clone" -> uat.cloneDatabase();
            case "bootstrap" -> uat.bootstrap();
            case "seed" -> uat.seed();
            case "verify" -> uat.verify();
            case "check-source" -> uat.checkSource();
            case "check-uat" -> uat.checkUat();
            case "probe" -> uat.probe();
            case "enable-attitude-setup" -> uat.enableAttitudeSetup();
            default -> throw new IllegalArgumentException("Unknown UAT action");
        }
    }

    private Connection sourceConnection() throws Exception {
        Connection c = DriverManager.getConnection(env.get("DB_URL"), env.get("DB_USER"), env.get("DB_PASS"));
        c.setReadOnly(true);
        return c;
    }

    private void enableAttitudeSetup() throws Exception {
        checkSource();
        token("setup");
        Path backup = STATE.resolve("before-attitude-setup-" + System.currentTimeMillis() + ".dump").toAbsolutePath();
        Path pg = Path.of(System.getenv().getOrDefault("APEX_UAT_PG_BIN", "C:/Program Files/PostgreSQL/18/bin"));
        pg(pg.resolve("pg_dump.exe"), List.of("--format=custom", "--no-owner", "--no-acl",
                "--file=" + backup, "--dbname=" + DATABASE));
        if (Files.size(backup) == 0) throw new IllegalStateException("UAT backup is empty");
        try (Connection c = target()) {
            Map<String, String> before = fingerprints(c);
            c.setAutoCommit(false);
            try {
                long role = Long.parseLong(scalar(c, "SELECT r.id FROM role r JOIN staff s ON s.role_id=r.id "
                        + "WHERE s.email='uat.central.setup@example.test' AND r.name='UAT Central Setup' "
                        + "AND r.is_deleted=false AND s.is_deleted=false AND s.account_status='ACTIVE' FOR UPDATE OF r"));
                if (!"1".equals(scalar(c, "SELECT count(*) FROM staff WHERE role_id=?", role))) {
                    throw new IllegalStateException("UAT setup Role is shared; refusing to broaden permissions");
                }
                if (!"1".equals(scalar(c, "SELECT count(*) FROM authority WHERE name='CAN_MANAGE_ATTITUDE_CONFIGURATION'"))) {
                    throw new IllegalStateException("Attitude setup authority is missing");
                }
                execute(c, "INSERT INTO role_authority(role_id,authority_id,created_at,updated_at) "
                        + "SELECT ?,a.id,now(),now() FROM authority a WHERE a.name='CAN_MANAGE_ATTITUDE_CONFIGURATION' "
                        + "AND NOT EXISTS(SELECT 1 FROM role_authority ra WHERE ra.role_id=? AND ra.authority_id=a.id)", role, role);
                if (!before.equals(fingerprints(c))) throw new IllegalStateException("Existing UAT performance data changed");
                c.commit();
                JSON.writerWithDefaultPrettyPrinter().writeValue(STATE.resolve("attitude-setup-grant.json").toFile(),
                        Map.of("database", DATABASE, "email", "uat.central.setup@example.test", "roleId", role,
                                "permission", "CAN_MANAGE_ATTITUDE_CONFIGURATION", "backup", backup.toString(),
                                "recordedAt", java.time.Instant.now().toString(),
                                "reason", "User-authorised disposable Attitude opening-date UAT setup"));
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
        tokens.remove("setup");
        call("GET", "/api/attitude-configurations/options", null, token("setup"), 200);
        checkSource();
        System.out.println("PASS: UAT setup account can administer Attitude Configuration; existing data preserved");
        System.out.println("UAT backup: " + backup);
    }

    private Connection target() throws Exception {
        Connection c = DriverManager.getConnection(targetUrl, env.get("DB_USER"), env.get("DB_PASS"));
        try {
            if (!DATABASE.equals(scalar(c, "SELECT current_database()")) || !MARKER.equals(scalar(c,
                    "SELECT shobj_description(oid,'pg_database') FROM pg_database WHERE datname=current_database()"))) {
                throw new IllegalStateException("Refusing access: UAT database marker does not match");
            }
            return c;
        } catch (Exception e) {
            c.close();
            throw e;
        }
    }

    private Map<String, String> fingerprints(Connection c) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : PROTECTED) result.put(table, scalar(c,
                "SELECT md5(COALESCE(string_agg(row_data, E'\\n' ORDER BY row_data),'')) "
                        + "FROM (SELECT row_to_json(t)::text AS row_data FROM public." + table + " t) rows"));
        return result;
    }

    private void checkSource() throws Exception {
        JsonNode baseline = JSON.readTree(STATE.resolve("source-fingerprints.json").toFile());
        try (Connection c = sourceConnection()) {
            if (!baseline.equals(JSON.valueToTree(fingerprints(c)))) {
                throw new IllegalStateException("Development data changed since the UAT clone; investigate without overwriting it");
            }
        }
        System.out.println("PASS: original periods, KPI plans and assessments match the pre-clone baseline");
    }

    private void checkUat() throws Exception {
        JsonNode fixture = JSON.readTree(STATE.resolve("fixtures.json").toFile());
        try (Connection c = target()) {
            if (!PERIOD_NAME.equals(scalar(c, "SELECT name FROM annual_kpi_review_period WHERE id=?",
                    fixture.path("periodId").asLong()))) {
                throw new IllegalStateException("Prepared UAT period is missing");
            }
        }
        System.out.println("PASS: marked UAT database and period verified; existing UAT progress is preserved");
    }

    private void probe() throws Exception {
        checkUat();
        long id = JSON.readTree(STATE.resolve("fixtures.json").toFile()).path("periodId").asLong();
        JsonNode period = call("GET", "/api/annual-kpi-review-periods/" + id, null, token("setup"), 200);
        if (!"CENTRAL_RESULTS".equals(period.path("assessmentModel").asText())) {
            throw new IllegalStateException("UAT backend did not return the prepared central-results period");
        }
        System.out.println("PASS: local backend is connected to central-results UAT period " + id);
    }

    private void cloneDatabase() throws Exception {
        Path pg = Path.of(System.getenv().getOrDefault("APEX_UAT_PG_BIN", "C:/Program Files/PostgreSQL/18/bin"));
        for (String binary : List.of("pg_dump.exe", "pg_restore.exe")) {
            if (!Files.isRegularFile(pg.resolve(binary))) throw new IllegalStateException("PostgreSQL tools missing: " + pg);
        }
        try (Connection c = sourceConnection()) {
            if (!"46".equals(scalar(c,
                    "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"))) {
                throw new IllegalStateException("Expected development schema V46; inspect migrations before cloning");
            }
            if (!"0".equals(scalar(c, "SELECT count(*) FROM pg_database WHERE datname='" + DATABASE + "'"))) {
                throw new IllegalStateException("UAT database exists; refusing to overwrite it");
            }
            JSON.writerWithDefaultPrettyPrinter().writeValue(STATE.resolve("source-fingerprints.json").toFile(), fingerprints(c));
        }
        Path dump = STATE.resolve("source-backup.dump").toAbsolutePath();
        pg(pg.resolve("pg_dump.exe"), List.of("--format=custom", "--no-owner", "--no-acl", "--file=" + dump,
                "--dbname=" + source.getPath().substring(1)));
        String admin = "jdbc:postgresql://" + source.getHost() + ":" + source.getPort() + "/postgres";
        try (Connection c = DriverManager.getConnection(admin, env.get("DB_USER"), env.get("DB_PASS"))) {
            execute(c, "CREATE DATABASE " + DATABASE);
            execute(c, "COMMENT ON DATABASE " + DATABASE + " IS '" + MARKER + "'");
        }
        pg(pg.resolve("pg_restore.exe"), List.of("--exit-on-error", "--no-owner", "--no-acl",
                "--dbname=" + DATABASE, dump.toString()));
        try (Connection c = target()) {
            if (!JSON.readTree(STATE.resolve("source-fingerprints.json").toFile())
                    .equals(JSON.valueToTree(fingerprints(c)))) {
                throw new IllegalStateException("Restored data differs from source backup");
            }
        }
        checkSource();
        System.out.println("Created isolated " + DATABASE + "; backup saved under " + STATE);
    }

    private void pg(Path executable, List<String> args) throws Exception {
        List<String> command = new ArrayList<>(List.of(executable.toString(), "--host=" + source.getHost(),
                "--port=" + source.getPort(), "--username=" + env.get("DB_USER")));
        command.addAll(args);
        ProcessBuilder process = new ProcessBuilder(command).inheritIO();
        process.environment().put("PGPASSWORD", env.get("DB_PASS"));
        if (process.start().waitFor() != 0) throw new IllegalStateException("PostgreSQL backup or restore failed");
    }

    private void bootstrap() throws Exception {
        try (Connection c = target()) {
            if (!"0".equals(scalar(c, "SELECT count(*) FROM staff WHERE email LIKE 'uat.central.%@example.test'"))) {
                throw new IllegalStateException("Disposable actors already exist; refusing a second bootstrap");
            }
            c.setAutoCommit(false);
            try {
                long department = id(c, "INSERT INTO org_chart(name,type,is_root,is_deleted,created_at,updated_at) "
                        + "VALUES ('UAT Central Results Department','D',false,false,now(),now()) RETURNING id");
                long setupRole = role(c, department, "Setup", false, null,
                        List.of("ROLE_USER", "CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD"));
                long mdRole = role(c, department, "MD", true, null,
                        List.of("ROLE_USER", "CAN_MANAGE_COMPANY_KPI", "CAN_APPROVE_DEPARTMENT_KPI"));
                long superiorRole = role(c, department, "Superior", true, null,
                        List.of("ROLE_USER", "CAN_MANAGE_DEPARTMENT_KPI", "CAN_REVIEW_INDIVIDUAL_KPI",
                                "CAN_REVIEW_KPI_ASSESSMENT"));
                actor(c, "setup", setupRole, null);
                UUID md = actor(c, "md", mdRole, null);
                UUID superior = actor(c, "superior", superiorRole, md);
                for (String frequency : List.of("MONTHLY", "QUARTERLY", "ANNUALLY")) {
                    long r = role(c, department, frequency, true, frequency, List.of("ROLE_USER"));
                    actor(c, frequency.toLowerCase(Locale.ROOT), r, superior);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
        checkSource();
        System.out.println("Created isolated setup, MD, Superior and three employee test accounts");
    }

    private long role(Connection c, long department, String name, boolean eligible, String frequency,
            List<String> authorities) throws Exception {
        long role = id(c, "INSERT INTO role(name,description,is_visible,is_deleted,created_at,updated_at,org_chart_id,"
                + "employee_level_id,performance_review_eligible,default_review_frequency) "
                + "VALUES (?, ?, true,false,now(),now(),?,(SELECT id FROM employee_level WHERE code='EXECUTIVE'),?,?) RETURNING id",
                "UAT Central " + name, "Disposable central KPI result test Role", department, eligible, frequency);
        for (String permission : authorities) {
            if (!"1".equals(scalar(c, "SELECT count(*) FROM authority WHERE name=?", permission))) {
                throw new IllegalStateException("Required permission missing: " + permission);
            }
            execute(c, "INSERT INTO role_authority(role_id,authority_id,created_at,updated_at) "
                    + "SELECT ?,id,now(),now() FROM authority WHERE name=?", role, permission);
        }
        return role;
    }

    private UUID actor(Connection c, String key, long role, UUID manager) throws Exception {
        UUID id = UUID.randomUUID();
        execute(c, "INSERT INTO staff(id,email,name,password,role_id,manager_id,is_first_login,account_status,is_deleted,created_at,updated_at) "
                + "VALUES (?,?,?,?,?,?,false,'ACTIVE',false,now(),now())", id,
                "uat.central." + key + "@example.test", "UAT Central " + key,
                new BCryptPasswordEncoder().encode(PASSWORD), role, manager);
        execute(c, "INSERT INTO staff_login_audit(staff_id,forgot_password_attempts,login_failed_attempts) VALUES (?,0,0)", id);
        execute(c, "INSERT INTO staff_profile(staff_id,created_at,updated_at) VALUES (?,now(),now())", id);
        execute(c, "INSERT INTO staff_refresh_token(staff_id,created_at,expires_at) VALUES (?,now(),now())", id);
        return id;
    }

    private void seed() throws Exception {
        try (Connection c = target()) {
            if (!"0".equals(scalar(c, "SELECT count(*) FROM annual_kpi_review_period WHERE name=?", PERIOD_NAME))) {
                throw new IllegalStateException("Disposable period already exists; refusing to create a duplicate");
            }
            if (!"3".equals(scalar(c, "SELECT count(*) FROM staff WHERE email IN "
                    + "('uat.central.monthly@example.test','uat.central.quarterly@example.test','uat.central.annually@example.test')"))) {
                throw new IllegalStateException("Run bootstrap before seeding plans");
            }
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("name", PERIOD_NAME);
        config.put("startDate", START.toString());
        config.put("endDate", END.toString());
        config.put("kpiSetupDeadline", START.toString());
        config.put("selfAssessmentDaysAfterCheckpoint", 20);
        config.put("superiorAssessmentDaysAfterSelfDeadline", 5);
        config.put("attitudeSelfAssessmentDeadline", END.plusDays(20).toString());
        config.put("superiorAttitudeEvaluationDeadline", END.plusDays(25).toString());
        config.put("appraisalRecommendationDeadline", END.plusDays(26).toString());
        config.put("hrFinalisationDeadline", END.plusDays(27).toString());
        config.put("kpiPerformanceWeight", 50);
        config.put("attitudeEvaluationWeight", 50);
        config.put("annualKpiConsolidationMethod", "FINAL_CHECKPOINT");
        JsonNode defaults = call("GET", "/api/annual-kpi-review-periods/creation-defaults?startDate=" + START,
                null, token("setup"), 200);
        config.put("employeeLevelConfigurations", defaults.get("employeeLevelConfigurations"));
        List<Object> frequencies = new ArrayList<>();
        try (Connection c = target()) {
            for (String f : List.of("MONTHLY", "QUARTERLY", "ANNUALLY")) {
                frequencies.add(Map.of("roleId", Long.parseLong(scalar(c, "SELECT id FROM role WHERE name=?", "UAT Central " + f)),
                        "reviewFrequency", f));
            }
        }
        config.put("roleConfigurations", frequencies);
        JsonNode period = call("POST", "/api/annual-kpi-review-periods", config, token("setup"), 201);
        long periodId = period.path("id").asLong();
        JsonNode published = call("POST", "/api/annual-kpi-review-periods/" + periodId + "/publish", null,
                token("setup"), 200);
        if (!"CENTRAL_RESULTS".equals(published.path("assessmentModel").asText())) {
            throw new IllegalStateException("New period is not using the central-results assessment model");
        }
        JsonNode company = call("POST", "/api/company-kpi-plans",
                Map.of("reviewPeriodId", periodId, "items", List.of(item("Company result target"))), token("md"), 201);
        call("POST", "/api/company-kpi-plans/" + company.path("id").asLong() + "/publish", null, token("md"), 200);
        long department;
        try (Connection c = target()) {
            department = Long.parseLong(scalar(c, "SELECT id FROM org_chart WHERE name='UAT Central Results Department'"));
        }
        JsonNode departmentPlan = call("POST", "/api/department-kpi-plans",
                Map.of("reviewPeriodId", periodId, "departmentId", department,
                        "items", List.of(item("Department result target"))), token("superior"), 201);
        long departmentPlanId = departmentPlan.path("id").asLong();
        call("POST", "/api/department-kpi-plans/" + departmentPlanId + "/submit", null, token("superior"), 200);
        call("POST", "/api/department-kpi-plans/" + departmentPlanId + "/approve", null, token("md"), 200);
        for (String employee : List.of("monthly", "quarterly", "annually")) {
            JsonNode individual = call("POST", "/api/individual-kpi-plans",
                    Map.of("reviewPeriodId", periodId, "items", List.of(item("Individual " + employee + " target"))),
                    token(employee), 201);
            long id = individual.path("id").asLong();
            call("POST", "/api/individual-kpi-plans/" + id + "/submit", null, token(employee), 200);
            call("POST", "/api/individual-kpi-plans/" + id + "/approve", null, token("superior"), 200);
        }
        Map<String, Object> manifest = Map.of("database", DATABASE, "periodId", periodId, "periodName", PERIOD_NAME,
                "startDate", START.toString(), "endDate", END.toString(), "departmentId", department);
        JSON.writerWithDefaultPrettyPrinter().writeValue(STATE.resolve("fixtures.json").toFile(), manifest);
        verify();
        System.out.println("UAT ready. Test-only password: " + PASSWORD);
    }

    private Map<String, Object> item(String name) {
        return Map.of("name", name, "perspective", "Financial", "kra", "UAT central result target",
                "target", "100 units", "weightage", 100, "scoringDefinitions", Map.of(
                        "1", "Below 40 units", "2", "40-69 units", "3", "70-99 units",
                        "4", "100-119 units", "5", "120 units or more"));
    }

    private void verify() throws Exception {
        JsonNode fixture = JSON.readTree(STATE.resolve("fixtures.json").toFile());
        long periodId = fixture.path("periodId").asLong();
        try (Connection c = target()) {
            if (!PERIOD_NAME.equals(scalar(c, "SELECT name FROM annual_kpi_review_period WHERE id=?", periodId))) {
                throw new IllegalStateException("Disposable period missing or changed");
            }
            if (!"0".equals(scalar(c, "SELECT count(*) FROM kpi_central_result WHERE review_period_id=?", periodId))
                    || !"0".equals(scalar(c, "SELECT count(*) FROM kpi_assessment WHERE participant_id IN "
                    + "(SELECT id FROM review_period_participant WHERE review_period_id=?)", periodId))) {
                throw new IllegalStateException("Results or assessments have already started; manual UAT data must be preserved");
            }
        }
        JsonNode options = call("GET", "/api/kpi-central-results/options?reviewPeriodId=" + periodId,
                null, token("md"), 200);
        if (options.isEmpty() || !"COMPANY".equals(options.get(0).path("level").asText())) {
            throw new IllegalStateException("MD cannot see the published Company result entry");
        }
        options = call("GET", "/api/kpi-central-results/options?reviewPeriodId=" + periodId,
                null, token("superior"), 200);
        if (options.isEmpty() || !"DEPARTMENT".equals(options.get(0).path("level").asText())) {
            throw new IllegalStateException("Superior cannot see the approved Department result entry");
        }
        for (String employee : List.of("monthly", "quarterly", "annually")) {
            String auth = token(employee);
            JsonNode checkpoints = call("GET", "/api/kpi-assessments/checkpoints?reviewPeriodId=" + periodId,
                    null, auth, 200);
            int expected = switch (employee) { case "monthly" -> 12; case "quarterly" -> 4; default -> 1; };
            if (checkpoints.size() != expected) throw new IllegalStateException(employee + " checkpoint count: " + checkpoints.size());
            long checkpointId = checkpoints.get(checkpoints.size() - 1).path("id").asLong();
            JsonNode assessment = call("GET", "/api/kpi-assessments/mine?checkpointId=" + checkpointId,
                    null, auth, 200);
            if (!"CENTRAL_RESULTS".equals(assessment.path("assessmentModel").asText())
                    || assessment.path("items").size() != 1
                    || !"INDIVIDUAL".equals(assessment.path("items").get(0).path("level").asText())
                    || !assessment.path("pendingResultLevels").toString().contains("COMPANY")
                    || !assessment.path("pendingResultLevels").toString().contains("DEPARTMENT")) {
                throw new IllegalStateException(employee + " is not ready for individual-only assessment");
            }
            System.out.println("PASS: " + employee + " has " + expected + " checkpoints, Individual-only assessment and pending central results");
        }
        checkSource();
    }

    private String token(String actor) throws Exception {
        if (tokens.containsKey(actor)) return tokens.get(actor);
        String email = "uat.central." + actor + "@example.test";
        JsonNode login = call("POST", "/auth/login", Map.of("email", email, "password", PASSWORD), null, 200);
        try (Connection c = target()) {
            if (!scalar(c, "SELECT id::text FROM staff WHERE email=?", email).equals(login.path("userId").asText())) {
                throw new IllegalStateException("HTTP backend is not connected to the marked UAT database");
            }
        }
        String token = login.path("accessToken").asText();
        tokens.put(actor, token);
        return token;
    }

    private JsonNode call(String method, String path, Object body, String token, int expected) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(api + path)).timeout(Duration.ofSeconds(45));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != expected) {
            throw new IllegalStateException(method + " " + path + ": expected " + expected + ", got "
                    + response.statusCode() + " " + response.body());
        }
        return response.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(response.body());
    }

    private static String scalar(Connection c, String sql, Object... args) throws Exception {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) throw new IllegalStateException("No row from verification query");
                return r.getString(1);
            }
        }
    }

    private static long id(Connection c, String sql, Object... args) throws Exception {
        return Long.parseLong(scalar(c, sql, args));
    }

    private static void execute(Connection c, String sql, Object... args) throws Exception {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            s.executeUpdate();
        }
    }
}
