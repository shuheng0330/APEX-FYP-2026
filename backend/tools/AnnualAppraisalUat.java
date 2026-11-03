import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Explicit local UAT rollout, never loaded by Spring or a migration. */
public class AnnualAppraisalUat {
    private static final String DATABASE="apex_central_results_uat";
    private static final String MARKER="APEX disposable central KPI results UAT clone";
    private static final Path STATE=Path.of("target","annual-appraisal-uat");
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Map<String,String> GRANTS=Map.of(
        "alice.johnson@apex.dev","CAN_SUBMIT_ANNUAL_APPRAISAL",
        "david.lim@apex.dev","CAN_REVIEW_ANNUAL_APPRAISAL");
    private final Dotenv env=Dotenv.configure().directory(".").load();
    private final URI source=URI.create(env.get("DB_URL").substring(5));
    private final String target="jdbc:postgresql://"+source.getHost()+":"+source.getPort()+"/"+DATABASE;

    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !Set.of("inspect","migrate","grant","verify").contains(args[0]))
            throw new IllegalArgumentException("inspect|migrate|grant|verify; targets the marked isolated UAT database only");
        var uat=new AnnualAppraisalUat();
        if(!Set.of("localhost","127.0.0.1").contains(uat.source.getHost()) || uat.source.getQuery()!=null
                || uat.source.getPort()<1 || uat.source.getPath().equals("/"+DATABASE))
            throw new IllegalStateException("Expected a separate plain local source database in .env");
        switch(args[0]) {
            case "inspect" -> uat.inspect(); case "migrate" -> uat.migrate();
            case "grant" -> uat.grant(); case "verify" -> uat.verify();
        }
    }
    private Connection connection(boolean sourceDatabase) throws Exception {
        var c=DriverManager.getConnection(sourceDatabase?env.get("DB_URL"):target,env.get("DB_USER"),env.get("DB_PASS"));
        if(!sourceDatabase && (!DATABASE.equals(scalar(c,"SELECT current_database()")) || !MARKER.equals(scalar(c,
                "SELECT shobj_description(oid,'pg_database') FROM pg_database WHERE datname=current_database()")))) {
            c.close();throw new IllegalStateException("Isolated UAT marker does not match");
        }
        return c;
    }
    private void inspect() throws Exception {
        try(var c=connection(false)) {
            c.setReadOnly(true);
            System.out.println("UAT: "+DATABASE+"; latest migration: "+scalar(c,"SELECT max(version::int) FROM flyway_schema_history WHERE type='SQL' AND success"));
            for(var entry:GRANTS.entrySet()) {
                var role=scalar(c,"SELECT r.id FROM role r JOIN staff s ON s.role_id=r.id WHERE s.email=? AND s.is_deleted=false AND s.account_status='ACTIVE' AND r.is_deleted=false",entry.getKey());
                System.out.println(entry.getKey()+"; role="+role+"; role members="+scalar(c,"SELECT count(*) FROM staff WHERE role_id=?",Long.parseLong(role)));
                System.out.println("Current authorities: "+scalar(c,"SELECT string_agg(a.name,', ' ORDER BY a.name) FROM role_authority ra JOIN authority a ON a.id=ra.authority_id WHERE ra.role_id=?",Long.parseLong(role)));
            }
            try(var s=c.createStatement();var rows=s.executeQuery("SELECT id,name,status FROM annual_kpi_review_period ORDER BY id")) {
                while(rows.next()) System.out.println("Period "+rows.getLong(1)+": "+rows.getString(2)+" ("+rows.getString(3)+")");
            }
            try(var s=c.prepareStatement("SELECT p.review_period_id,r.name,p.staff_name FROM review_period_participant p JOIN staff employee ON employee.id=p.staff_id JOIN staff manager ON manager.id=employee.manager_id JOIN annual_kpi_review_period r ON r.id=p.review_period_id WHERE manager.email=? ORDER BY p.review_period_id,p.staff_name")) {
                s.setString(1,"alice.johnson@apex.dev");
                try(var rows=s.executeQuery()) {while(rows.next()) System.out.println("Alice's team: period "+rows.getLong(1)+" ("+rows.getString(2)+") / "+rows.getString(3));}
            }
        }
    }
    private Map<String,String> fingerprints(Connection c,Set<String> excluded) throws Exception {
        var tables=new ArrayList<String>();var result=new LinkedHashMap<String,String>();
        try(var s=c.createStatement();var rows=s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")) {
            while(rows.next()) if(!excluded.contains(rows.getString(1))) tables.add(rows.getString(1));
        }
        for(var table:tables) result.put(table,scalar(c,"SELECT md5(COALESCE(string_agg(row_data,E'\\n' ORDER BY row_data),'')) FROM (SELECT row_to_json(t)::text row_data FROM public.\""+table.replace("\"","\"\"")+"\" t) q"));
        return result;
    }
    private void migrate() throws Exception {
        Files.createDirectories(STATE);
        try(var c=connection(false)) {
            c.setReadOnly(true);
            if(!"47".equals(scalar(c,"SELECT max(version::int) FROM flyway_schema_history WHERE type='SQL' AND success")))
                throw new IllegalStateException("This explicit rollout expects V47, not a different migration state");
        }
        Map<String,String> sourceBefore;
        try(var c=connection(true)) {c.setReadOnly(true);sourceBefore=fingerprints(c,Set.of());}
        JSON.writerWithDefaultPrettyPrinter().writeValue(STATE.resolve("source-before.json").toFile(),sourceBefore);
        var runner=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),
                "--class-path",System.getProperty("java.class.path"),"ApplyAnnualReviewPeriodFoundation.java","--apply",pgDump().toString());
        runner.environment().put("DB_URL",target);runner.inheritIO();
        if(runner.start().waitFor()!=0) throw new IllegalStateException("Isolated migration runner failed; do not start the new backend");
        assertSourceUnchanged(sourceBefore);
        System.out.println("PASS: V48 migrated in marked UAT only; development database fingerprints unchanged");
    }
    private Path pgDump() {return Path.of("C:/Program Files/PostgreSQL/18/bin/pg_dump.exe");}
    private void grant() throws Exception {
        Map<String,String> sourceBefore;
        try(var c=connection(true)) {c.setReadOnly(true);sourceBefore=fingerprints(c,Set.of());}
        Files.createDirectories(STATE);
        var backup=STATE.resolve("before-approved-grants-"+System.currentTimeMillis()+".dump").toAbsolutePath();
        var dump=new ProcessBuilder(pgDump().toString(),"--host="+source.getHost(),"--port="+source.getPort(),
            "--username="+env.get("DB_USER"),"--dbname="+DATABASE,"--format=custom","--file="+backup);
        dump.environment().put("PGPASSWORD",env.get("DB_PASS"));dump.inheritIO();
        if(dump.start().waitFor()!=0 || Files.size(backup)==0) throw new IllegalStateException("UAT grant backup failed");
        var audit=new ArrayList<Map<String,Object>>();
        try(var c=connection(false)) {
            c.setAutoCommit(false);
            try {
                var before=fingerprints(c,Set.of("role_authority"));
                for(var entry:GRANTS.entrySet()) {
                    long role=Long.parseLong(scalar(c,"SELECT r.id FROM role r JOIN staff s ON s.role_id=r.id WHERE s.email=? AND s.is_deleted=false AND s.account_status='ACTIVE' AND r.is_deleted=false FOR UPDATE OF r",entry.getKey()));
                    if(!"1".equals(scalar(c,"SELECT count(*) FROM staff WHERE role_id=?",role)))
                        throw new IllegalStateException("Role is shared; refusing to broaden the approved account grant: "+entry.getKey());
                    if(!"1".equals(scalar(c,"SELECT count(*) FROM authority WHERE name=?",entry.getValue()))) throw new IllegalStateException("Apply V48 first");
                    int inserted;
                    try(var s=c.prepareStatement("INSERT INTO role_authority(role_id,authority_id,created_at,updated_at) SELECT ?,id,now(),now() FROM authority WHERE name=? AND NOT EXISTS(SELECT 1 FROM role_authority WHERE role_id=? AND authority_id=authority.id)")) {
                        s.setLong(1,role);s.setString(2,entry.getValue());s.setLong(3,role);inserted=s.executeUpdate();
                    }
                    audit.add(Map.of("email",entry.getKey(),"roleId",role,"permission",entry.getValue(),"inserted",inserted));
                }
                if(!before.equals(fingerprints(c,Set.of("role_authority")))) throw new IllegalStateException("Non-permission UAT data changed");
                c.commit();
            } catch(Exception e) {c.rollback();throw e;}
        }
        assertSourceUnchanged(sourceBefore);
        JSON.writerWithDefaultPrettyPrinter().writeValue(STATE.resolve("approved-test-grants.json").toFile(),
                Map.of("database",DATABASE,"approvedGrants",audit,"backup",backup.toString(),"at",Instant.now().toString()));
        System.out.println("PASS: explicit Alice submission / David review grants recorded; all other UAT data and source database unchanged");
    }
    private void assertSourceUnchanged(Map<String,String> before) throws Exception {
        try(var c=connection(true)) {c.setReadOnly(true);if(!before.equals(fingerprints(c,Set.of()))) throw new IllegalStateException("Development database changed during rollout; investigate");}
    }
    private void verify() throws Exception {
        inspect();
        try(var c=connection(false)) {
            c.setReadOnly(true);
            if(!"1".equals(scalar(c,"SELECT count(*) FROM flyway_schema_history WHERE version='48' AND success"))) throw new IllegalStateException("V48 is missing");
            for(var entry:GRANTS.entrySet()) if(!"1".equals(scalar(c,"SELECT count(*) FROM staff s JOIN role_authority ra ON ra.role_id=s.role_id JOIN authority a ON a.id=ra.authority_id WHERE s.email=? AND a.name=?",entry.getKey(),entry.getValue())))
                throw new IllegalStateException("Approved grant missing: "+entry.getKey());
            System.out.println("Annual appraisals saved: "+scalar(c,"SELECT count(*) FROM annual_appraisal"));
        }
        System.out.println("PASS: UAT schema and both approved grants verified");
    }
    private static String scalar(Connection c,String sql,Object... args) throws Exception {
        try(var s=c.prepareStatement(sql)) {for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            try(var rows=s.executeQuery()) {if(!rows.next()) throw new IllegalStateException("Expected database row not found");return rows.getString(1);}}
    }
}
