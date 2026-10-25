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
    private static final int LATEST_VERSION = 50;
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
        System.out.println("Local migration target database: " + target.getPath().substring(1));
        if (!Files.isRegularFile(Path.of(args[1]))) throw new IllegalArgumentException("pg_dump executable not found");
        if (!Files.isRegularFile(Path.of("src/main/resources/db/migration/annual-kpi/V27__annual_review_period_foundation.sql"))) {
            throw new IllegalStateException("Run from the backend directory; the Phase 1 migration file is missing");
        }
        boolean alreadyApplied;
        boolean permissionAlreadyApplied;
        boolean levelsAlreadyApplied;
        boolean refinementAlreadyApplied;
        long extraApplied;
        long staffCount;
        long roleCount;
        var preservedRows=new java.util.LinkedHashMap<String,String>();
        var centralPreservedRows=new java.util.LinkedHashMap<String,String>();
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
            extraApplied = scalar(connection, "SELECT count(*) FROM public.flyway_schema_history WHERE type='SQL' AND success "
                    + "AND version::int BETWEEN 31 AND " + LATEST_VERSION);
            for (int version = 31; version <= LATEST_VERSION; version++) {
                long applied = scalar(connection, "SELECT count(*) FROM public.flyway_schema_history WHERE type='SQL' AND success AND version='" + version + "'");
                if (applied != (version < 31 + extraApplied ? 1 : 0) || (applied > 0 && !refinementAlreadyApplied))
                    throw new IllegalStateException("Non-contiguous Phase 2 migration history");
            }
            if (historyCount != 0 && (scalar(connection, "SELECT count(*) FROM public.flyway_schema_history "
                    + "WHERE version='26' AND type='BASELINE' AND success") != 1
                    || (permissionAlreadyApplied && !alreadyApplied)
                    || (levelsAlreadyApplied && !permissionAlreadyApplied)
                    || (refinementAlreadyApplied && !levelsAlreadyApplied)
                    || historyCount != 1 + (alreadyApplied ? 1 : 0) + (permissionAlreadyApplied ? 1 : 0) + (levelsAlreadyApplied ? 1 : 0) + (refinementAlreadyApplied ? 1 : 0) + extraApplied)) {
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
            long kpiTables = scalar(connection, "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    + "AND table_name IN ('kpi_plan','kpi','kpi_scoring_definition','employee_kpi_assignment')");
            if (kpiTables != (extraApplied > 0 ? 4 : 0))
                throw new IllegalStateException("Partial/untracked KPI foundation detected; reconcile before migration");
            long snapshotColumn=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='annual_kpi_review_period' AND column_name='participants_snapshotted_at'");
            if(snapshotColumn!=(extraApplied>=2?1:0)) throw new IllegalStateException("Partial/untracked V32 snapshot schema");
            long publicationColumns = scalar(connection, "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='kpi_plan' AND column_name IN ('published_at','published_by','published_late')");
            if (publicationColumns != (extraApplied >= 2 ? 3 : 0))
                throw new IllegalStateException("Partial/untracked V32 Company publication schema");
            long reviewColumns = scalar(connection, "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='kpi_plan' AND column_name IN ('submitted_at','submitted_by','submitted_late',"
                    + "'reviewed_at','reviewed_by','reviewed_late','return_reason')");
            long compatibleReviewColumns = scalar(connection, "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='kpi_plan' AND column_name IN ('submitted_at','submitted_by','submitted_late',"
                    + "'reviewed_at','reviewed_by','reviewed_late','return_reason') AND is_nullable='YES' AND column_default IS NULL "
                    + "AND udt_name=CASE column_name WHEN 'submitted_at' THEN 'timestamptz' WHEN 'reviewed_at' THEN 'timestamptz' "
                    + "WHEN 'submitted_by' THEN 'uuid' WHEN 'reviewed_by' THEN 'uuid' WHEN 'return_reason' THEN 'text' ELSE 'bool' END");
            boolean compatibleHibernateReview = extraApplied == 2 && reviewColumns == 7 && compatibleReviewColumns == 7;
            if (reviewColumns != (extraApplied >= 3 ? 7 : 0) && !compatibleHibernateReview)
                throw new IllegalStateException("Partial/untracked V33 Department review schema");
            long individualRoutingColumn = scalar(connection, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_schema='public' AND table_name='kpi_plan' AND column_name='submitted_to_superior_id'");
            long compatibleIndividualRouting = scalar(connection, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_schema='public' AND table_name='kpi_plan' AND column_name='submitted_to_superior_id' "
                    + "AND udt_name='uuid' AND is_nullable='YES' AND column_default IS NULL");
            if (individualRoutingColumn != (extraApplied >= 4 ? 1 : 0)
                    && !(extraApplied == 3 && individualRoutingColumn == 1 && compatibleIndividualRouting == 1))
                throw new IllegalStateException("Partial/untracked V34 Individual review routing schema");
            long requestReasonColumns=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='individual_kpi_assistance_authorization' AND column_name='request_reason'");
            long compatibleRequestReason=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='individual_kpi_assistance_authorization' AND column_name='request_reason' "
                    + "AND data_type='text' AND is_nullable='YES' AND column_default IS NULL");
            if(requestReasonColumns!=(extraApplied>=8?1:0)
                    && !(extraApplied==7 && requestReasonColumns==1 && compatibleRequestReason==1))
                throw new IllegalStateException("Partial/untracked V38 assistance request reason schema");
            if(extraApplied==7 && compatibleRequestReason==1)
                System.out.println("Verified compatible nullable TEXT request_reason column; V38 will preserve it and add validation.");
            long assessmentTables=scalar(connection,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    + "AND table_name IN ('kpi_assessment','kpi_assessment_item','kpi_assessment_evidence')");
            if(assessmentTables!=(extraApplied>=9?3:0))
                throw new IllegalStateException("Partial/untracked V39 assessment schema; reconcile before migration");
            long attitudeTables=scalar(connection,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    + "AND table_name IN ('attitude_configuration','attitude_criterion','attitude_rating_definition','attitude_role_format_mapping')");
            long attitudeBinding=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='annual_kpi_review_period' AND column_name='attitude_configuration_id'");
            if(attitudeTables!=(extraApplied>=10?4:0) || attitudeBinding!=(extraApplied>=10?1:0))
                throw new IllegalStateException("Partial/untracked V40 attitude configuration schema; reconcile before migration");
            long attitudeAssessments=scalar(connection,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    + "AND table_name IN ('attitude_assessment','attitude_assessment_item')");
            if(attitudeAssessments!=(extraApplied>=11?2:0))
                throw new IllegalStateException("Partial/untracked V41 attitude assessment schema; reconcile before migration");
            long attitudeReviewPermission=scalar(connection,"SELECT count(*) FROM public.authority "
                    + "WHERE name='CAN_REVIEW_ATTITUDE_EVALUATION'");
            if(attitudeReviewPermission!=(extraApplied>=12?1:0))
                throw new IllegalStateException("Partial/untracked V42 attitude review permission; reconcile before migration");
            long returnColumns=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='kpi_plan' AND column_name IN ('latest_return_reason','latest_returned_at','latest_returned_by','latest_return_items')");
            long compatibleReturnColumns=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='kpi_plan' AND is_nullable='YES' AND column_default IS NULL AND "
                    + "((column_name='latest_return_reason' AND udt_name='text') OR (column_name='latest_returned_at' AND udt_name='timestamptz') "
                    + "OR (column_name='latest_returned_by' AND udt_name='uuid') OR (column_name='latest_return_items' AND udt_name='jsonb'))");
            if((extraApplied>=13 && returnColumns!=4) || returnColumns!=compatibleReturnColumns || (returnColumns!=0 && returnColumns!=4))
                throw new IllegalStateException("Partial/untracked V43 return feedback schema; reconcile before migration");
            if(extraApplied==12 && returnColumns==4)
                System.out.println("Verified compatible nullable return feedback columns; V43 will preserve values and add constraints.");
            long versionTables=scalar(connection,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    + "AND table_name IN ('kpi_plan_version','kpi_assessment_plan_version')");
            long versionColumns=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND "
                    + "((table_name='annual_kpi_review_period' AND column_name IN ('kpi_amendments_enabled','kpi_amendments_close_date','kpi_amendments_changed_at','kpi_amendments_changed_by')) "
                    + "OR (table_name='kpi_plan' AND column_name='current_version_id') OR (table_name='kpi' AND column_name IN ('version_id','predecessor_id')))");
            if(versionTables!=(extraApplied>=14?2:0) || versionColumns!=(extraApplied>=14?7:0))
                throw new IllegalStateException("Partial/untracked V44 version schema; reconcile before migration");
            long individualRevisionColumns=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='kpi_plan_version' AND column_name IN ('preparation_mode','revision_superior_id')");
            long compatibleIndividualRevisionColumns=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='kpi_plan_version' AND is_nullable='YES' AND column_default IS NULL AND "
                    + "((column_name='preparation_mode' AND udt_name='varchar' AND character_maximum_length=20) "
                    + "OR (column_name='revision_superior_id' AND udt_name='uuid'))");
            boolean compatibleHibernateIndividualRevision=extraApplied==14 && individualRevisionColumns==2 && compatibleIndividualRevisionColumns==2;
            if(individualRevisionColumns!=(extraApplied>=15?2:0) && !compatibleHibernateIndividualRevision)
                throw new IllegalStateException("Partial/untracked V45 Individual revision schema; reconcile before migration");
            if(compatibleHibernateIndividualRevision && scalar(connection,"SELECT count(*) FROM kpi_plan_version WHERE preparation_mode IS NOT NULL OR revision_superior_id IS NOT NULL")!=0)
                throw new IllegalStateException("Untracked Individual revision metadata is populated; investigate before migration");
            if(compatibleHibernateIndividualRevision)
                System.out.println("Verified compatible empty Hibernate-created revision columns; V45 will preserve them and add workflow constraints.");
            long centralTables=scalar(connection,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    + "AND table_name IN ('kpi_central_result','kpi_central_result_item','kpi_central_result_evidence',"
                    + "'company_kpi_result_delegate','kpi_checkpoint_result')");
            long modelColumns=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name IN ('annual_kpi_review_period','kpi_assessment') AND column_name='assessment_model'");
            long centralFunctions=scalar(connection,"SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace "
                    + "WHERE n.nspname='public' AND p.proname IN ('guard_period_assessment_model','guard_central_result',"
                    + "'guard_central_result_child','guard_company_result_delegate_audit','guard_checkpoint_result_bindings')");
            long centralTriggers=scalar(connection,"SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid "
                    + "JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND NOT t.tgisinternal "
                    + "AND t.tgname IN ('guard_period_assessment_model','guard_central_result','guard_central_item',"
                    + "'guard_central_evidence','guard_company_result_delegate_audit','guard_checkpoint_result_bindings')");
            long centralScopeConstraints=scalar(connection,"SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace "
                    + "WHERE n.nspname='public' AND c.conname IN ('uq_period_assessment_model','uq_kpi_central_version','fk_assessment_period_model')");
            long centralPermission=scalar(connection,"SELECT count(*) FROM public.authority WHERE name='CAN_ENTER_COMPANY_KPI_RESULTS'");
            boolean centralApplied=extraApplied>=16;
            boolean compatibleHibernateCentral=!centralApplied && extraApplied==15 && centralTables==5 && modelColumns==1
                    && centralFunctions==0 && centralTriggers==0 && centralScopeConstraints==0 && centralPermission==0
                    && compatibleEmptyCentralFoundation(connection,"public");
            if((centralTables!=(centralApplied?5:0) || modelColumns!=(centralApplied?2:0)
                    || centralFunctions!=(centralApplied?5:0) || centralTriggers!=(centralApplied?6:0)
                    || centralScopeConstraints!=(centralApplied?3:0) || centralPermission!=(centralApplied?1:0))
                    && !compatibleHibernateCentral)
                throw new IllegalStateException("Partial/untracked V46 central result schema; reconcile before migration");
            if(compatibleHibernateCentral)
                System.out.println("Verified exact empty Hibernate-created V46 tables and legacy assessment model column; backup precedes constraint/default adoption.");
            if(centralApplied && scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND column_name='assessment_model' AND data_type='character varying' AND character_maximum_length=24 "
                    + "AND is_nullable='NO' AND ((table_name='annual_kpi_review_period' AND column_default="
                    + "'''CENTRAL_RESULTS''::character varying') OR (table_name='kpi_assessment' AND column_default="
                    + "'''LEGACY_ALL_LEVELS''::character varying'))")!=2)
                throw new IllegalStateException("V46 assessment model defaults/types do not match the reviewed foundation");
            long openingColumns=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='annual_kpi_review_period' AND column_name='attitude_self_assessment_start_date'");
            long compatibleOpeningColumns=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='annual_kpi_review_period' AND column_name='attitude_self_assessment_start_date' "
                    + "AND data_type='date' AND is_nullable='YES' AND column_default IS NULL");
            if(openingColumns!=compatibleOpeningColumns || openingColumns!=(extraApplied>=17?1:0)
                    && !(extraApplied==16 && compatibleOpeningColumns==1))
                throw new IllegalStateException("Partial/untracked V47 Attitude opening date schema; reconcile before migration");
            long openingConstraints=scalar(connection,"SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace "
                    + "WHERE n.nspname='public' AND c.conname IN ('ck_period_attitude_opening_required','ck_period_attitude_opening_order')");
            if(openingConstraints!=(extraApplied>=17?2:0))
                throw new IllegalStateException("Partial/untracked V47 Attitude opening date constraints; reconcile before migration");
            if(extraApplied==16 && openingColumns==1 && scalar(connection,"SELECT count(*) FROM annual_kpi_review_period "
                    + "WHERE status<>'DRAFT' AND attitude_self_assessment_start_date IS NOT NULL "
                    + "AND attitude_self_assessment_start_date IS DISTINCT FROM start_date")!=0)
                throw new IllegalStateException("Untracked published Attitude opening dates require review before migration");
            long annualAppraisalTables=scalar(connection,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    +"AND table_name IN ('annual_appraisal','annual_appraisal_activity')");
            long annualAppraisalPermissions=scalar(connection,"SELECT count(*) FROM authority WHERE name IN "
                    +"('CAN_SUBMIT_ANNUAL_APPRAISAL','CAN_REVIEW_ANNUAL_APPRAISAL')");
            long annualAppraisalFunctions=scalar(connection,"SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace "
                    +"WHERE n.nspname='public' AND p.proname IN ('guard_annual_appraisal','guard_annual_appraisal_activity')");
            long annualAppraisalOwnerKey=scalar(connection,"SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace "
                    +"WHERE n.nspname='public' AND c.conname='uq_annual_appraisal_owner'");
            if(annualAppraisalTables!=(extraApplied>=18?2:0) || annualAppraisalPermissions!=(extraApplied>=18?2:0)
                    || annualAppraisalFunctions!=(extraApplied>=18?2:0) || annualAppraisalOwnerKey!=(extraApplied>=18?1:0))
                throw new IllegalStateException("Partial/untracked V48 annual appraisal schema; reconcile before migration");
            if(extraApplied>=15) {
                long frequencyColumns=scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND "
                        +"((table_name='annual_kpi_review_period' AND column_name='frequency_policy') OR "
                        +"(table_name='review_period_participant' AND column_name='department_configuration_id') OR "
                        +"(table_name='kpi_checkpoint_result' AND column_name='company_checkpoint_id'))");
                long departmentTables=scalar(connection,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                        +"AND table_name='review_period_department_configuration'");
                if(frequencyColumns!=(extraApplied>=19?3:0) || departmentTables!=(extraApplied>=19?1:0))
                    throw new IllegalStateException("Partial/untracked V49 frequency schema; reconcile before migration");
                if(extraApplied>=20 && scalar(connection,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='user_notification'")!=1)
                    throw new IllegalStateException("Applied V50 notification table is missing");
                // Snapshot every existing application table, not only the KPI subset. The new
                // model/Attitude opening columns and explicitly added authority are excluded.
                try(var statement=connection.createStatement();var tables=statement.executeQuery(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema='public' "
                                + "AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history' ORDER BY table_name")) {
                    while(tables.next()) {
                        String table=tables.getString(1);
                        centralPreservedRows.put(table,centralRowDigest(connection,table));
                    }
                }
            }
            if(extraApplied==13) for(var table:List.of("annual_kpi_review_period","review_period_role_configuration",
                    "review_period_employee_level_configuration","review_checkpoint","review_period_participant","kpi_plan","kpi",
                    "kpi_scoring_definition","employee_kpi_assignment","kpi_assessment","kpi_assessment_item","kpi_assessment_evidence"))
                preservedRows.put(table,rowDigest(connection,table));
            if(extraApplied==14) for(var table:List.of("annual_kpi_review_period","review_period_role_configuration",
                    "review_period_employee_level_configuration","review_checkpoint","review_period_participant","kpi_plan","kpi",
                    "kpi_scoring_definition","employee_kpi_assignment","kpi_plan_version","kpi_assessment_plan_version",
                    "kpi_assessment","kpi_assessment_item","kpi_assessment_evidence"))
                preservedRows.put(table,rowDigest(connection,table));
            connection.rollback();
        }

        var flyway = Flyway.configure().dataSource(url, user, password)
                .schemas("public").defaultSchema("public")
                .locations("filesystem:src/main/resources/db/migration/annual-kpi")
                .baselineVersion("26").baselineDescription("Inherited Hibernate-managed development schema")
                .baselineOnMigrate(false).outOfOrder(false).cleanDisabled(true).target(Integer.toString(LATEST_VERSION)).load();
        if (!refinementAlreadyApplied || extraApplied < LATEST_VERSION - 30) {
            var backupDirectory = Path.of(System.getProperty("user.home"), ".apex", "database-backups");
            Files.createDirectories(backupDirectory);
            var backup = backupDirectory.resolve("before-annual-kpi-V" + LATEST_VERSION + "-"
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
            for(var entry:preservedRows.entrySet()) if(!entry.getValue().equals(rowDigest(connection,entry.getKey())))
                throw new IllegalStateException("Unexpected existing data change in "+entry.getKey()+"; investigate using the backup");
            for(var entry:centralPreservedRows.entrySet()) if(!entry.getValue().equals(centralRowDigest(connection,entry.getKey())))
                throw new IllegalStateException("Unexpected existing data change in "+entry.getKey()+"; investigate using the backup");
            if(extraApplied<17 && scalar(connection,"SELECT count(*) FROM annual_kpi_review_period WHERE status<>'DRAFT' "
                    + "AND attitude_self_assessment_start_date IS DISTINCT FROM start_date")!=0)
                throw new IllegalStateException("V47 did not preserve published-period Attitude availability; investigate using the backup");
            if(extraApplied==15) {
                if(scalar(connection,"SELECT count(*) FROM public.annual_kpi_review_period WHERE assessment_model<>'LEGACY_ALL_LEVELS'")!=0
                        || scalar(connection,"SELECT count(*) FROM public.kpi_assessment WHERE assessment_model<>'LEGACY_ALL_LEVELS'")!=0)
                    throw new IllegalStateException("V46 changed an existing assessment model; investigate using the backup");
                for(String table:List.of("kpi_central_result","kpi_central_result_item","kpi_central_result_evidence",
                        "company_kpi_result_delegate","kpi_checkpoint_result"))
                    if(scalar(connection,"SELECT count(*) FROM public."+table)!=0)
                        throw new IllegalStateException("V46 unexpectedly populated "+table+"; investigate using the backup");
            }
            if(scalar(connection,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' "
                    +"AND table_name IN ('annual_appraisal','annual_appraisal_activity')")!=2
                    || scalar(connection,"SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid "
                    +"JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND NOT t.tgisinternal "
                    +"AND t.tgname IN ('guard_annual_appraisal','guard_annual_appraisal_activity')")!=2)
                throw new IllegalStateException("V48 annual appraisal tables or history guards are missing");
            if(extraApplied<18 && (scalar(connection,"SELECT count(*) FROM annual_appraisal")!=0
                    || scalar(connection,"SELECT count(*) FROM annual_appraisal_activity")!=0))
                throw new IllegalStateException("V48 unexpectedly populated annual appraisals");
            if(extraApplied<19 && (scalar(connection,"SELECT count(*) FROM annual_kpi_review_period WHERE frequency_policy<>'ROLE_BASED'")!=0
                    || scalar(connection,"SELECT count(*) FROM review_period_department_configuration")!=0
                    || scalar(connection,"SELECT count(*) FROM review_period_participant WHERE department_configuration_id IS NOT NULL")!=0
                    || scalar(connection,"SELECT count(*) FROM kpi_checkpoint_result WHERE company_checkpoint_id IS DISTINCT FROM checkpoint_id")!=0))
                throw new IllegalStateException("V49 compatibility backfill failed; investigate using the backup");
            if(scalar(connection,"SELECT count(*) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid "
                    +"JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND NOT t.tgisinternal "
                    +"AND t.tgname IN ('guard_period_frequency_policy','guard_period_department_configuration',"
                    +"'guard_participant_department_frequency','guard_department_checkpoint_schedule','guard_company_checkpoint_source')")!=5)
                throw new IllegalStateException("V49 frequency protection triggers are missing");
            if(scalar(connection,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='user_notification' "
                    +"AND column_name IN ('id','recipient_id','type','title','body','link','dedupe_key','created_at','read_at','email_status','email_attempts','email_error','emailed_at')")!=13
                    || scalar(connection,"SELECT count(*) FROM pg_index i JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=i.indkey[0] "
                    +"WHERE i.indrelid='public.user_notification'::regclass AND i.indisunique AND i.indisvalid AND i.indimmediate "
                    +"AND i.indpred IS NULL AND i.indexprs IS NULL AND i.indnkeyatts=1 AND a.attname='dedupe_key'")!=1)
                throw new IllegalStateException("V50 notification columns or atomic dedupe index are missing");
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
        if(!preservedRows.isEmpty() || !centralPreservedRows.isEmpty())
            System.out.println("Existing period configuration, KPIs, assignments, assessment answers, evidence and scores verified unchanged.");
    }

    private static long scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            try (ResultSet rows = statement.executeQuery(sql)) { rows.next(); return rows.getLong(1); }
        }
    }
    private static String rowDigest(Connection connection,String table) throws SQLException {
        String row="(to_jsonb(t)-ARRAY['current_version_id','version_id','predecessor_id','kpi_amendments_enabled',"
                +"'kpi_amendments_close_date','kpi_amendments_changed_at','kpi_amendments_changed_by','preparation_mode','revision_superior_id','attitude_self_assessment_start_date'])::text";
        try(var statement=connection.createStatement();var rows=statement.executeQuery(
                "SELECT md5(coalesce(string_agg("+row+",',' ORDER BY "+row+"),'')) FROM public."+table+" t")) {
            rows.next();return rows.getString(1);
        }
    }
    private static String centralRowDigest(Connection connection,String table) throws SQLException {
        String quotedTable="\""+table.replace("\"","\"\"")+"\"";
        String row=table.equals("annual_kpi_review_period")
                ?"(to_jsonb(t)-ARRAY['assessment_model','attitude_self_assessment_start_date','frequency_policy'])::text":
                table.equals("review_period_participant")?"(to_jsonb(t)-'department_configuration_id')::text":
                table.equals("kpi_checkpoint_result")?"(to_jsonb(t)-'company_checkpoint_id')::text":
                table.equals("kpi_assessment")?"(to_jsonb(t)-'assessment_model')::text":"to_jsonb(t)::text";
        String filter=table.equals("authority")?" WHERE name NOT IN ('CAN_ENTER_COMPANY_KPI_RESULTS',"
                +"'CAN_SUBMIT_ANNUAL_APPRAISAL','CAN_REVIEW_ANNUAL_APPRAISAL')":"";
        try(var statement=connection.createStatement();var rows=statement.executeQuery(
                "SELECT md5(coalesce(string_agg("+row+",',' ORDER BY "+row+"),'')) FROM public."+quotedTable+" t"+filter)) {
            rows.next();return rows.getString(1);
        }
    }

    // This exception is intentionally narrow: no extra columns, defaults, FKs, indexes,
    // populated rows or unrecognised checks may be adopted from an untracked ORM schema.
    static boolean compatibleEmptyCentralFoundation(Connection connection,String schema) throws SQLException {
        try(var statement=connection.prepareStatement("SELECT count(*) FROM information_schema.columns WHERE table_schema=? "
                +"AND table_name='annual_kpi_review_period' AND column_name='assessment_model'")) {
            statement.setString(1,schema);
            try(var rows=statement.executeQuery()) {rows.next();if(rows.getLong(1)!=0) return false;}
        }
        var expected=java.util.Map.of(
                "kpi_central_result",List.of("id:bigint:NO:d","review_period_id:bigint:NO:","checkpoint_id:bigint:NO:",
                        "review_frequency:character varying(20):NO:","plan_version_id:bigint:NO:","level:character varying(20):NO:",
                        "edition_number:integer:NO:","status:character varying(20):NO:","previous_edition_id:bigint:YES:",
                        "correction_reason:text:YES:","created_at:timestamp(6) with time zone:NO:","updated_at:timestamp(6) with time zone:NO:",
                        "created_by:uuid:NO:","updated_by:uuid:NO:","published_at:timestamp(6) with time zone:YES:",
                        "published_by:uuid:YES:","lock_version:bigint:NO:"),
                "kpi_central_result_item",List.of("id:bigint:NO:d","result_id:bigint:NO:","kpi_id:bigint:NO:",
                        "plan_version_id:bigint:NO:","actual_achievement:text:YES:","point:integer:YES:","comment:text:YES:"),
                "kpi_central_result_evidence",List.of("id:bigint:NO:d","item_id:bigint:NO:","storage_key:character varying(100):NO:",
                        "original_filename:character varying(255):NO:","content_type:character varying(100):NO:","size_bytes:bigint:NO:",
                        "uploaded_by:uuid:NO:","uploaded_at:timestamp(6) with time zone:NO:"),
                "company_kpi_result_delegate",List.of("id:bigint:NO:d","review_period_id:bigint:NO:","staff_id:uuid:NO:",
                        "assigned_by:uuid:NO:","assigned_at:timestamp(6) with time zone:NO:","revoked_by:uuid:YES:","revoked_at:timestamp(6) with time zone:YES:"),
                "kpi_checkpoint_result",List.of("id:bigint:NO:d","participant_id:bigint:NO:","review_period_id:bigint:NO:",
                        "checkpoint_id:bigint:NO:","review_frequency:character varying(20):NO:","company_version_id:bigint:YES:",
                        "department_version_id:bigint:YES:","individual_version_id:bigint:YES:","company_result_id:bigint:YES:",
                        "department_result_id:bigint:YES:","score:numeric(7,4):YES:","calculated_at:timestamp(6) with time zone:YES:",
                        "calculation_history:jsonb:NO:","lock_version:bigint:NO:"));
        String quotedSchema="\""+schema.replace("\"","\"\"")+"\"";
        for(var table:expected.entrySet()) {
            var columns=new java.util.HashSet<String>();
            try(var statement=connection.prepareStatement("SELECT a.attname,format_type(a.atttypid,a.atttypmod),"
                    +"a.attnotnull,a.attidentity,a.attgenerated,pg_get_expr(d.adbin,d.adrelid) "
                    +"FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace "
                    +"LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum "
                    +"WHERE n.nspname=? AND c.relname=? AND a.attnum>0 AND NOT a.attisdropped")) {
                statement.setString(1,schema);statement.setString(2,table.getKey());
                try(var rows=statement.executeQuery()) {while(rows.next()) {
                    if(!rows.getString(5).isEmpty() || rows.getString(6)!=null) return false;
                    columns.add(rows.getString(1)+":"+rows.getString(2)+":"+(rows.getBoolean(3)?"NO":"YES")+":"+rows.getString(4));
                }}
            }
            if(!columns.equals(new java.util.HashSet<>(table.getValue()))) return false;
            if(scalar(connection,"SELECT count(*) FROM "+quotedSchema+"."+table.getKey())!=0) return false;
            var expectedChecks=switch(table.getKey()) {
                case "kpi_central_result" -> java.util.Set.of("level=ANYARRAY['COMPANY','DEPARTMENT','INDIVIDUAL']",
                        "review_frequency=ANYARRAY['MONTHLY','QUARTERLY','ANNUALLY']","status=ANYARRAY['DRAFT','PUBLISHED','SUPERSEDED']");
                case "kpi_checkpoint_result" -> java.util.Set.of("review_frequency=ANYARRAY['MONTHLY','QUARTERLY','ANNUALLY']");
                default -> java.util.Set.<String>of();
            };
            var checks=new java.util.HashSet<String>();int primaryKeys=0;
            try(var statement=connection.prepareStatement("SELECT c.contype,pg_get_constraintdef(c.oid) "
                    +"FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace "
                    +"WHERE n.nspname=? AND t.relname=? AND c.contype<>'n'")) {
                statement.setString(1,schema);statement.setString(2,table.getKey());
                try(var rows=statement.executeQuery()) {while(rows.next()) {
                    if(rows.getString(1).equals("p") && rows.getString(2).equals("PRIMARY KEY (id)")) primaryKeys++;
                    else if(rows.getString(1).equals("c")) checks.add(rows.getString(2).replace("CHECK","")
                            .replace("::character varying[]","").replace("::text[]","").replace("::character varying","")
                            .replace("::text","").replaceAll("[()\\s]",""));
                    else return false;
                }}
            }
            if(primaryKeys!=1 || !checks.equals(expectedChecks)) return false;
            try(var statement=connection.prepareStatement("SELECT count(*) FROM pg_trigger g JOIN pg_class t ON t.oid=g.tgrelid "
                    +"JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname=? AND t.relname=? AND NOT g.tgisinternal")) {
                statement.setString(1,schema);statement.setString(2,table.getKey());
                try(var rows=statement.executeQuery()) {rows.next();if(rows.getLong(1)!=0) return false;}
            }
            try(var statement=connection.prepareStatement("SELECT count(*) FROM pg_index i JOIN pg_class t ON t.oid=i.indrelid "
                    +"JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname=? AND t.relname=? AND NOT i.indisprimary")) {
                statement.setString(1,schema);statement.setString(2,table.getKey());
                try(var rows=statement.executeQuery()) {rows.next();if(rows.getLong(1)!=0) return false;}
            }
        }
        try(var statement=connection.prepareStatement("SELECT count(*) FROM information_schema.columns WHERE table_schema=? "
                +"AND table_name='kpi_assessment' AND column_name='assessment_model' AND udt_name='varchar' "
                +"AND character_maximum_length=24 AND is_nullable='NO' AND column_default IS NULL")) {
            statement.setString(1,schema);
            try(var rows=statement.executeQuery()) {rows.next();if(rows.getLong(1)!=1) return false;}
        }
        return scalar(connection,"SELECT count(*) FROM "+quotedSchema+".kpi_assessment WHERE assessment_model<>'LEGACY_ALL_LEVELS'")==0;
    }
}
