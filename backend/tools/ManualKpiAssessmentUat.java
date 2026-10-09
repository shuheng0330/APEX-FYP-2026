import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.io.*;

/** Explicit local UAT fixture. Never discovered by Spring or Flyway. */
public class ManualKpiAssessmentUat {
    static final String DATABASE = "apex_manual_uat";
    static final String MARKER = "APEX disposable KPI assessment UAT clone";
    static final String PASSWORD = "ApexUat2026!";
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path STATE = Path.of("target", "manual-kpi-uat");
    static final List<String> PROTECTED = List.of("annual_kpi_review_period", "review_period_role_configuration",
            "review_period_employee_level_configuration", "review_checkpoint", "review_period_participant",
            "kpi_plan", "kpi", "kpi_scoring_definition", "employee_kpi_assignment", "kpi_assessment",
            "kpi_assessment_item", "kpi_assessment_evidence", "role", "role_authority", "org_chart", "staff");
    final Dotenv env = Dotenv.configure().directory(".").load();
    final URI source = URI.create(env.get("DB_URL").substring(5));
    final String targetUrl = "jdbc:postgresql://" + source.getHost() + ":" + source.getPort() + "/" + DATABASE;
    final String api;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    final Map<String,String> tokens = new HashMap<>();

    ManualKpiAssessmentUat(String api) {
        if (!List.of("localhost", "127.0.0.1").contains(source.getHost()) || source.getQuery() != null
                || source.getPort() < 1 || source.getPath().equals("/" + DATABASE))
            throw new IllegalArgumentException("Expected a distinct plain local source PostgreSQL URL");
        var endpoint = URI.create(api);
        if (!"http".equals(endpoint.getScheme()) || !List.of("localhost", "127.0.0.1").contains(endpoint.getHost())
                || !endpoint.getPath().isEmpty() || endpoint.getQuery() != null)
            throw new IllegalArgumentException("Use a local HTTP UAT backend origin");
        this.api = api;
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("clone|bootstrap|seed|verify|smoke|negative|check-source|close|mail [local backend origin]");
        Files.createDirectories(STATE);
        if (args[0].equals("mail")) { captureMail(); return; }
        var uat = new ManualKpiAssessmentUat(args.length > 1 ? args[1] : "http://localhost:8082");
        switch (args[0]) {
            case "clone" -> uat.cloneDatabase();
            case "bootstrap" -> uat.bootstrap();
            case "seed" -> uat.seed();
            case "verify" -> uat.verify();
            case "smoke" -> uat.smoke();
            case "negative" -> uat.negativeChecks();
            case "check-source" -> uat.checkSource();
            case "close" -> uat.closeFixture();
            default -> throw new IllegalArgumentException("Unknown UAT action");
        }
    }
    Connection sourceConnection() throws Exception {
        var c = DriverManager.getConnection(env.get("DB_URL"), env.get("DB_USER"), env.get("DB_PASS"));
        c.setReadOnly(true); return c;
    }
    Connection target() throws Exception {
        var c = DriverManager.getConnection(targetUrl, env.get("DB_USER"), env.get("DB_PASS"));
        try {
            if (!DATABASE.equals(scalar(c,"SELECT current_database()")) || !MARKER.equals(scalar(c,
                    "SELECT shobj_description(oid,'pg_database') FROM pg_database WHERE datname=current_database()")))
                throw new IllegalStateException("Refusing writes: database does not carry the UAT marker");
            return c;
        } catch (Exception e) { c.close(); throw e; }
    }
    Map<String,String> fingerprints(Connection c) throws Exception {
        Map<String,String> result = new TreeMap<>();
        for (String table : PROTECTED) result.put(table,scalar(c,"SELECT md5(COALESCE(string_agg(row_data, E'\\n' ORDER BY row_data),'')) "
                + "FROM (SELECT row_to_json(t)::text AS row_data FROM public." + table + " t) rows"));
        return result;
    }
    void checkSource() throws Exception {
        var baseline = JSON.readTree(STATE.resolve("source-fingerprints.json").toFile());
        try (var c=sourceConnection()) {
            if (!JSON.valueToTree(fingerprints(c)).equals(baseline))
                throw new IllegalStateException("Source data differs from the baseline; investigate, do not overwrite it");
        }
        System.out.println("PASS: original review periods, KPI data, assessments and actor configuration are unchanged");
    }
    void cloneDatabase() throws Exception {
        Path pg = Path.of(System.getenv().getOrDefault("APEX_UAT_PG_BIN", "C:/Program Files/PostgreSQL/18/bin"));
        if (!Files.isRegularFile(pg.resolve("pg_dump.exe")) || !Files.isRegularFile(pg.resolve("pg_restore.exe")))
            throw new IllegalStateException("Set APEX_UAT_PG_BIN to the local PostgreSQL bin directory");
        try (var c=sourceConnection()) {
            if (!"39".equals(scalar(c,"SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")))
                throw new IllegalStateException("Assess the source migration history first; this fixture expects V39");
            if (!"0".equals(scalar(c,"SELECT count(*) FROM pg_database WHERE datname='"+DATABASE+"'")))
                throw new IllegalStateException("UAT database already exists; refusing to overwrite or drop it");
            JSON.writerWithDefaultPrettyPrinter().writeValue(STATE.resolve("source-fingerprints.json").toFile(),fingerprints(c));
        }
        var dump=STATE.resolve("source-backup.dump").toAbsolutePath();
        pg(pg.resolve("pg_dump.exe"),List.of("--format=custom","--no-owner","--no-acl","--file="+dump,
                "--dbname="+source.getPath().substring(1)));
        String adminUrl="jdbc:postgresql://"+source.getHost()+":"+source.getPort()+"/postgres";
        try(var c=DriverManager.getConnection(adminUrl,env.get("DB_USER"),env.get("DB_PASS"));var s=c.createStatement()) {
            s.execute("CREATE DATABASE "+DATABASE);
            s.execute("COMMENT ON DATABASE "+DATABASE+" IS '"+MARKER+"'");
        }
        pg(pg.resolve("pg_restore.exe"),List.of("--exit-on-error","--no-owner","--no-acl","--dbname="+DATABASE,dump.toString()));
        try(var c=target()) {
            if(!JSON.readTree(STATE.resolve("source-fingerprints.json").toFile()).equals(JSON.valueToTree(fingerprints(c))))
                throw new IllegalStateException("Restored data does not match the source baseline");
        }
        checkSource(); System.out.println("Created isolated database "+DATABASE+"; backup is under "+STATE);
    }
    void pg(Path program,List<String> args) throws Exception {
        var command=new ArrayList<>(List.of(program.toString(),"--host="+source.getHost(),"--port="+source.getPort(),"--username="+env.get("DB_USER")));
        command.addAll(args);var process=new ProcessBuilder(command).inheritIO();
        process.environment().put("PGPASSWORD",env.get("DB_PASS"));
        if(process.start().waitFor()!=0) throw new IllegalStateException("PostgreSQL backup/restore failed");
    }
    void bootstrap() throws Exception {
        try(var c=target()) {
            c.setAutoCommit(false);
            if(!"0".equals(scalar(c,"SELECT count(*) FROM staff WHERE email LIKE 'uat.%@example.test'")))
                throw new IllegalStateException("Disposable accounts already exist; refusing a second bootstrap");
            long department=id(c,"INSERT INTO org_chart(name,type,is_root,is_deleted,created_at,updated_at) "
                    +"VALUES ('UAT Assessment Department','D',false,false,now(),now()) RETURNING id");
            long setupRole=role(c,department,"Setup",false,null,List.of("ROLE_USER","CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD"));
            long mdRole=role(c,department,"MD",true,null,List.of("ROLE_USER","CAN_MANAGE_COMPANY_KPI","CAN_APPROVE_DEPARTMENT_KPI"));
            long superiorRole=role(c,department,"Superior",true,null,List.of("ROLE_USER","CAN_MANAGE_DEPARTMENT_KPI",
                    "CAN_REVIEW_INDIVIDUAL_KPI","CAN_REVIEW_KPI_ASSESSMENT"));
            UUID setup=actor(c,"setup",setupRole,null), md=actor(c,"md",mdRole,null), superior=actor(c,"superior",superiorRole,md);
            for(String frequency:List.of("MONTHLY","QUARTERLY","ANNUALLY")) {
                long r=role(c,department,frequency,true,frequency,List.of("ROLE_USER"));
                actor(c,frequency.toLowerCase(Locale.ROOT),r,superior);
            }
            long outsiderRole=role(c,department,"Outsider",true,null,List.of("ROLE_USER"));
            actor(c,"outsider",outsiderRole,superior);
            c.commit();
        }
        checkSource();System.out.println("Provisioned disposable actors only in the marked UAT database");
    }
    long role(Connection c,long department,String name,boolean eligible,String frequency,List<String> authorities) throws Exception {
        long role=id(c,"INSERT INTO role(name,description,is_visible,is_deleted,created_at,updated_at,org_chart_id,"+
                "employee_level_id,performance_review_eligible,default_review_frequency) VALUES (?, ?, true,false,now(),now(),?,"+
                "(SELECT id FROM employee_level WHERE code='EXECUTIVE'),?,?) RETURNING id","UAT "+name,"Disposable manual assessment test Role",department,eligible,frequency);
        for(String permission:authorities) {
            if(!"1".equals(scalar(c,"SELECT count(*) FROM authority WHERE name=?",permission)))
                throw new IllegalStateException("Missing existing authority "+permission);
            execute(c,"INSERT INTO role_authority(role_id,authority_id,created_at,updated_at) "
                    +"SELECT ?,id,now(),now() FROM authority WHERE name=?",role,permission);
        }
        return role;
    }
    UUID actor(Connection c,String key,long role,UUID manager) throws Exception {
        UUID id=UUID.randomUUID();
        execute(c,"INSERT INTO staff(id,email,name,password,role_id,manager_id,is_first_login,account_status,is_deleted,created_at,updated_at) "
                +"VALUES (?,?,?,?,?,?,false,'ACTIVE',false,now(),now())",id,"uat."+key+"@example.test","UAT "+key,
                new BCryptPasswordEncoder().encode(PASSWORD),role,manager);
        execute(c,"INSERT INTO staff_login_audit(staff_id,forgot_password_attempts,login_failed_attempts) VALUES (?,0,0)",id);
        execute(c,"INSERT INTO staff_profile(staff_id,created_at,updated_at) VALUES (?,now(),now())",id);
        execute(c,"INSERT INTO staff_refresh_token(staff_id,created_at,expires_at) VALUES (?,now(),now())",id);
        return id;
    }
    String token(String actor) throws Exception {
        if(tokens.containsKey(actor)) return tokens.get(actor);
        var login=call("POST","/auth/login",Map.of("email","uat."+actor+"@example.test","password",PASSWORD),null,200);
        try(var c=target()) {
            if(!scalar(c,"SELECT id::text FROM staff WHERE email=?","uat."+actor+"@example.test").equals(login.path("userId").asText()))
                throw new IllegalStateException("HTTP backend is not connected to the UAT database");
        }
        String token=login.path("accessToken").asText();tokens.put(actor,token);return token;
    }
    JsonNode call(String method,String path,Object body,String token,int expected) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(api+path)).timeout(Duration.ofSeconds(45));
        if(token!=null) builder.header("Authorization","Bearer "+token);
        if(body!=null) builder.header("Content-Type","application/json");
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        var response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=expected) throw new IllegalStateException(method+" "+path+": expected "+expected+", got "+response.statusCode()+" "+response.body());
        return response.body().isBlank()?JSON.createObjectNode():JSON.readTree(response.body());
    }
    Object item(String name) {
        return Map.of("name",name,"perspective","Financial","kra","UAT performance target","target","100 units",
                "weightage",100,"scoringDefinitions",Map.of("1","Below 40 units","2","40-69 units","3","70-99 units","4","100-119 units","5","120 units or more"));
    }
    void seed() throws Exception {
        try(var c=target()) {
            if(!"0".equals(scalar(c,"SELECT count(*) FROM annual_kpi_review_period WHERE name LIKE 'UAT - %'")))
                throw new IllegalStateException("Periods already seeded; no reset or overwrite is performed");
            // Supply inherited login support rows for disposable accounts, without resetting existing rows.
            execute(c,"INSERT INTO staff_refresh_token(staff_id,created_at,expires_at) SELECT id,now(),now() FROM staff "
                    +"WHERE email LIKE 'uat.%@example.test' ON CONFLICT (staff_id) DO NOTHING");
            UUID superior=UUID.fromString(scalar(c,"SELECT id FROM staff WHERE email='uat.superior@example.test'"));
            c.setAutoCommit(false);
            for(String frequency:List.of("MONTHLY","QUARTERLY","ANNUALLY")) {
                long role=Long.parseLong(scalar(c,"SELECT id FROM role WHERE name=?","UAT "+frequency));
                actor(c,"smoke."+frequency.toLowerCase(Locale.ROOT),role,superior);
            }
            c.commit();
        }
        LocalDate today=LocalDate.now();
        LocalDate end=LocalDate.of(today.getYear(),((today.getMonthValue()-1)/3)*3+1,1).minusDays(1);
        var manifest=JSON.createObjectNode();manifest.put("setupDate",today.toString());manifest.put("database",DATABASE);
        var cases=manifest.putArray("periods");
        for(int i=0;i<5;i++) {
            String label=List.of("Ready","Overdue","Missing Department","Closed","Upcoming").get(i);
            LocalDate e=end.minusYears(i),start=e.minusYears(1).plusDays(1);
            if(i==4) { start=LocalDate.of(today.getYear()+1,1,1);e=start.plusYears(1).minusDays(1); }
            int selfDays=i==0?Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(e,today))+5:5;
            var config=new LinkedHashMap<String,Object>();config.put("name","UAT - "+label);
            config.put("startDate",start.toString());config.put("endDate",e.toString());config.put("kpiSetupDeadline",start.toString());
            config.put("selfAssessmentDaysAfterCheckpoint",selfDays);config.put("superiorAssessmentDaysAfterSelfDeadline",5);
            config.put("attitudeSelfAssessmentDeadline",e.plusDays(selfDays).toString());
            config.put("superiorAttitudeEvaluationDeadline",e.plusDays(selfDays+5).toString());
            config.put("appraisalRecommendationDeadline",e.plusDays(selfDays+6).toString());config.put("hrFinalisationDeadline",e.plusDays(selfDays+7).toString());
            config.put("kpiPerformanceWeight",50);config.put("attitudeEvaluationWeight",50);config.put("annualKpiConsolidationMethod","FINAL_CHECKPOINT");
            var defaults=call("GET","/api/annual-kpi-review-periods/creation-defaults?startDate="+start,null,token("setup"),200);
            config.put("employeeLevelConfigurations",defaults.get("employeeLevelConfigurations"));
            var roles=new ArrayList<Object>();
            try(var c=target()) {
                for(String f:List.of("MONTHLY","QUARTERLY","ANNUALLY")) roles.add(Map.of("roleId",Long.parseLong(scalar(c,"SELECT id FROM role WHERE name=?","UAT "+f)),"reviewFrequency",f));
            }
            config.put("roleConfigurations",roles);
            var draft=call("POST","/api/annual-kpi-review-periods",config,token("setup"),201);
            long period=draft.path("id").asLong();
            call("POST","/api/annual-kpi-review-periods/"+period+"/publish",null,token("setup"),200);
            if(i==4) {
                var entry=cases.addObject();entry.put("id",period);entry.put("name","UAT - "+label);
                entry.put("startDate",start.toString());entry.put("endDate",e.toString());
                continue;
            }
            var company=call("POST","/api/company-kpi-plans",Map.of("reviewPeriodId",period,"items",List.of(item("Company target"))),token("md"),201);
            call("POST","/api/company-kpi-plans/"+company.path("id").asLong()+"/publish",null,token("md"),200);
            long department;try(var c=target()) {department=Long.parseLong(scalar(c,"SELECT id FROM org_chart WHERE name='UAT Assessment Department'"));}
            var plan=call("POST","/api/department-kpi-plans",Map.of("reviewPeriodId",period,"departmentId",department,"items",List.of(item("Department target"))),token("superior"),201);
            call("POST","/api/department-kpi-plans/"+plan.path("id").asLong()+"/submit",null,token("superior"),200);
            if(i!=2) call("POST","/api/department-kpi-plans/"+plan.path("id").asLong()+"/approve",null,token("md"),200);
            for(String employee:List.of("monthly","quarterly","annually","smoke.monthly","smoke.quarterly","smoke.annually")) {
                var individual=call("POST","/api/individual-kpi-plans",Map.of("reviewPeriodId",period,"items",List.of(item("Individual target"))),token(employee),201);
                long id=individual.path("id").asLong();call("POST","/api/individual-kpi-plans/"+id+"/submit",null,token(employee),200);
                call("POST","/api/individual-kpi-plans/"+id+"/approve",null,token("superior"),200);
                if(i==3 && !employee.startsWith("smoke.")) {
                    var checkpoints=call("GET","/api/kpi-assessments/checkpoints?reviewPeriodId="+period,null,token(employee),200);
                    long checkpoint=checkpoints.get(checkpoints.size()-1).path("id").asLong();
                    var view=call("GET","/api/kpi-assessments/mine?checkpointId="+checkpoint,null,token(employee),200);
                    call("POST","/api/kpi-assessments",Map.of("checkpointId",checkpoint,"items",List.of(Map.of(
                            "assignmentId",view.path("items").get(0).path("assignmentId").asLong(),"selfPoint",3,
                            "selfComment","Saved before closing this disposable test period"))),token(employee),201);
                }
            }
            var entry=cases.addObject();entry.put("id",period);entry.put("name","UAT - "+label);entry.put("departmentPlanId",plan.path("id").asLong());
            entry.put("startDate",start.toString());entry.put("endDate",e.toString());entry.put("finalSelfDeadline",e.plusDays(selfDays).toString());
            System.out.println("Prepared "+entry.path("name").asText()+" (period "+period+") through existing Phase 1-2 APIs");
        }
        JSON.writerWithDefaultPrettyPrinter().writeValue(STATE.resolve("fixtures.json").toFile(),manifest);
        try(var doc=new PDDocument()) {doc.addPage(new PDPage());doc.save(STATE.resolve("evidence.pdf").toFile());}
        var image=new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB);
        javax.imageio.ImageIO.write(image,"png",STATE.resolve("evidence.png").toFile());
        Files.writeString(STATE.resolve("invalid-evidence.pdf"),"Not a PDF");
        closeFixture();checkSource();System.out.println("UAT ready. Account password: "+PASSWORD);
    }
    JsonNode fixtures() throws Exception {return JSON.readTree(STATE.resolve("fixtures.json").toFile());}
    long period(String label) throws Exception {
        for(var p:fixtures().path("periods")) if(p.path("name").asText().equals("UAT - "+label)) return p.path("id").asLong();
        throw new IllegalStateException("Missing fixture "+label);
    }
    void closeFixture() throws Exception {
        long id=period("Closed");
        try(var c=target()) {
            if(!"UAT - Closed".equals(scalar(c,"SELECT name FROM annual_kpi_review_period WHERE id=?",id))) throw new IllegalStateException("Invalid disposable Closed period");
            execute(c,"UPDATE annual_kpi_review_period SET status='CLOSED',closed_at=now() WHERE id=? AND name='UAT - Closed' AND status='OPEN'",id);
        }
        System.out.println("Marked only UAT - Closed as Closed (test fixture, not a production Close workflow)");
    }
    void verify() throws Exception {
        for(String actor:List.of("monthly","quarterly","annually")) {
            var t=token(actor);
            for(String label:List.of("Ready","Overdue","Missing Department","Closed")) {
                var checkpoints=call("GET","/api/kpi-assessments/checkpoints?reviewPeriodId="+period(label),null,t,200);
                var checkpoint=checkpoints.get(checkpoints.size()-1);
                var assessment=call("GET","/api/kpi-assessments/mine?checkpointId="+checkpoint.path("id").asLong(),null,t,200);
                String frequency=actor.toUpperCase(Locale.ROOT);
                if(!frequency.equals(checkpoint.path("reviewFrequency").asText())) throw new IllegalStateException("Wrong checkpoint frequency");
                if(assessment.path("canSaveDraft").asBoolean()==label.equals("Closed")) throw new IllegalStateException("Incorrect Draft availability for "+label);
                if(assessment.path("items").size()!=(label.equals("Missing Department")?2:3)) throw new IllegalStateException("Incorrect assignments for "+label);
                if(label.equals("Missing Department") && !assessment.path("missingLevels").toString().contains("DEPARTMENT")) throw new IllegalStateException("Missing Department reason not reported");
                if(label.equals("Ready") && assessment.path("overdue").asBoolean() || label.equals("Overdue") && !assessment.path("overdue").asBoolean()) throw new IllegalStateException("Incorrect lateness");
            }
            System.out.println("PASS: "+actor+" checkpoint, assignments, ready/overdue/missing/Closed context");
        }
        var outsider=call("GET","/api/kpi-assessments/periods",null,token("outsider"),200);
        if(outsider.size()!=0) throw new IllegalStateException("Outsider was enrolled");
        var monthly=call("GET","/api/kpi-assessments/checkpoints?reviewPeriodId="+period("Ready"),null,token("monthly"),200);
        call("GET","/api/kpi-assessments/mine?checkpointId="+monthly.get(0).path("id").asLong(),null,token("quarterly"),403);
        call("GET","/api/kpi-assessments/checkpoints?reviewPeriodId="+period("Ready"),null,token("outsider"),403);
        var upcoming=call("GET","/api/kpi-assessments/checkpoints?reviewPeriodId="+period("Upcoming"),null,token("monthly"),200);
        if(upcoming.get(0).path("available").asBoolean()) throw new IllegalStateException("Upcoming checkpoint incorrectly available");
        checkSource();System.out.println("PASS: frequency boundary and non-participant access denied; no assessments created");
    }
    void smoke() throws Exception {
        for(String frequency:List.of("monthly","quarterly","annually")) {
            String actor="smoke."+frequency, t=token(actor);
            for(String label:List.of("Ready","Overdue")) {
                var schedule=call("GET","/api/kpi-assessments/checkpoints?reviewPeriodId="+period(label),null,t,200);
                long checkpoint=schedule.get(schedule.size()-1).path("id").asLong();
                var view=call("GET","/api/kpi-assessments/mine?checkpointId="+checkpoint,null,t,200);
                if(!view.path("id").isNull()) throw new IllegalStateException("Smoke checkpoint is already used; no overwrite allowed");
                var draft=call("POST","/api/kpi-assessments",Map.of("checkpointId",checkpoint,"items",List.of()),t,201);
                long assessment=draft.path("id").asLong(),itemId=draft.path("items").get(0).path("id").asLong();
                call("POST","/api/kpi-assessments/"+assessment+"/submit",null,t,400);
                upload(itemId,STATE.resolve("invalid-evidence.pdf"),t,400);
                long fileId=upload(itemId,STATE.resolve("evidence.pdf"),t,201).path("id").asLong();
                var download=http.send(HttpRequest.newBuilder(URI.create(api+"/api/kpi-assessments/evidence/"+fileId))
                        .header("Authorization","Bearer "+t).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
                if(download.statusCode()!=200 || download.body().length==0) throw new IllegalStateException("Evidence download failed");
                call("GET","/api/kpi-assessments/"+assessment,null,token("outsider"),403);
                call("GET","/api/kpi-assessments/evidence/"+fileId,null,token("outsider"),403);
                call("DELETE","/api/kpi-assessments/evidence/"+fileId,null,t,204);
                fileId=upload(itemId,STATE.resolve("evidence.png"),t,201).path("id").asLong();
                var answers=new ArrayList<Object>();
                for(var item:draft.path("items")) answers.add(Map.of("assignmentId",item.path("assignmentId").asLong(),"selfPoint",4,"selfComment","UAT smoke answer"));
                var request=Map.of("checkpointId",checkpoint,"items",answers);
                call("PUT","/api/kpi-assessments/"+assessment,request,t,200);
                var saved=call("GET","/api/kpi-assessments/"+assessment,null,t,200);
                if(!saved.path("canSubmit").asBoolean()) throw new IllegalStateException("Complete Draft should be submittable");
                var submitted=call("POST","/api/kpi-assessments/"+assessment+"/submit",null,t,200);
                if(!"PENDING_REVIEW".equals(submitted.path("status").asText()) || submitted.path("submittedLate").asBoolean()!=label.equals("Overdue")
                        || !submitted.path("checkpointScore").isNull()) throw new IllegalStateException("Incorrect submission result");
                call("PUT","/api/kpi-assessments/"+assessment,request,t,400);
                call("POST","/api/kpi-assessments/"+assessment+"/submit",null,t,400);
                call("DELETE","/api/kpi-assessments/evidence/"+fileId,null,t,400);
                upload(itemId,STATE.resolve("evidence.png"),t,400);
                call("GET","/api/kpi-assessments/"+assessment,null,token("superior"),200);
                System.out.println("PASS: "+frequency+" "+label+" Draft/missing points/evidence/submission/lateness/read-only/access checks");
            }
        }
        checkSource();System.out.println("Manual employee checkpoints were not used by smoke testing");
    }
    JsonNode upload(long itemId,Path file,String token,int expected) throws Exception {
        String boundary="uat-"+UUID.randomUUID();var body=new ByteArrayOutputStream();
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+file.getFileName()+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(Files.readAllBytes(file));body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var response=http.send(HttpRequest.newBuilder(URI.create(api+"/api/kpi-assessments/items/"+itemId+"/evidence"))
                .timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=expected) throw new IllegalStateException("Evidence expected "+expected+", got "+response.statusCode()+" "+response.body());
        return JSON.readTree(response.body());
    }
    void negativeChecks() throws Exception {
        String t=token("smoke.monthly");
        var checkpoints=call("GET","/api/kpi-assessments/checkpoints?reviewPeriodId="+period("Missing Department"),null,t,200);
        long checkpoint=checkpoints.get(checkpoints.size()-1).path("id").asLong();
        var view=call("GET","/api/kpi-assessments/mine?checkpointId="+checkpoint,null,t,200);
        var draft=view.path("id").isNull()?call("POST","/api/kpi-assessments",Map.of("checkpointId",checkpoint,"items",List.of()),t,201):view;
        long assessment=draft.path("id").asLong(),item=draft.path("items").get(0).path("id").asLong();
        var failed=call("POST","/api/kpi-assessments/"+assessment+"/submit",null,t,400);
        if(!failed.toString().contains("Department")) throw new IllegalStateException("Missing level rejection has no useful reason");
        Path oversized=STATE.resolve("oversized-evidence.png");
        try(var file=new RandomAccessFile(oversized.toFile(),"rw")) {file.setLength(10L*1024*1024+1);}
        upload(item,oversized,t,400);
        call("GET","/api/kpi-assessments/"+assessment,null,token("superior"),403);
        var otherSchedule=call("GET","/api/kpi-assessments/checkpoints?reviewPeriodId="+period("Missing Department"),null,token("quarterly"),200);
        var otherView=call("GET","/api/kpi-assessments/mine?checkpointId="+otherSchedule.get(0).path("id").asLong(),null,token("quarterly"),200);
        call("PUT","/api/kpi-assessments/"+assessment,Map.of("checkpointId",checkpoint,"items",List.of(Map.of(
                "assignmentId",otherView.path("items").get(0).path("assignmentId").asLong(),"selfPoint",3))),t,403);
        var closed=call("GET","/api/kpi-assessments/checkpoints?reviewPeriodId="+period("Closed"),null,token("monthly"),200);
        var saved=call("GET","/api/kpi-assessments/mine?checkpointId="+closed.get(closed.size()-1).path("id").asLong(),null,token("monthly"),200);
        call("PUT","/api/kpi-assessments/"+saved.path("id").asLong(),Map.of("checkpointId",saved.path("checkpoint").path("id").asLong(),"items",List.of()),token("monthly"),400);
        call("POST","/api/kpi-assessments/"+saved.path("id").asLong()+"/submit",null,token("monthly"),400);
        var future=call("GET","/api/kpi-assessments/checkpoints?reviewPeriodId="+period("Upcoming"),null,t,200);
        call("POST","/api/kpi-assessments",Map.of("checkpointId",future.get(0).path("id").asLong(),"items",List.of()),t,400);
        checkSource();System.out.println("PASS: missing-level submission, oversize evidence, private Draft, foreign assignment, Closed writes and Upcoming save rejected");
    }
    static String scalar(Connection c,String sql,Object... args) throws Exception {
        try(var p=c.prepareStatement(sql)) {bind(p,args);try(var r=p.executeQuery()){if(!r.next()) throw new IllegalStateException("Expected a result");return r.getString(1);}}
    }
    static long id(Connection c,String sql,Object... args) throws Exception {return Long.parseLong(scalar(c,sql,args));}
    static void execute(Connection c,String sql,Object... args) throws Exception {try(var p=c.prepareStatement(sql)){bind(p,args);p.executeUpdate();}}
    static void bind(PreparedStatement p,Object[] args) throws Exception {for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);}

    static void captureMail() throws Exception {
        Path inbox=STATE.resolve("mail");Files.createDirectories(inbox);
        try(var server=new ServerSocket(1025,25,InetAddress.getByName("127.0.0.1"))) {
            System.out.println("Local-only SMTP capture listening on 127.0.0.1:1025");
            while(true) {
                try(var socket=server.accept();var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                        var writer=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8),true)) {
                    socket.setSoTimeout(30000);writer.print("220 APEX UAT local mail sink\r\n");writer.flush();
                    String line;
                    while((line=reader.readLine())!=null) {
                        if(line.equalsIgnoreCase("DATA")) {
                            writer.print("354 End with .\r\n");writer.flush();var message=new StringBuilder();
                            while((line=reader.readLine())!=null && !line.equals(".")) message.append(line.startsWith("..")?line.substring(1):line).append("\r\n");
                            Files.writeString(inbox.resolve(UUID.randomUUID()+".eml"),message);
                            writer.print("250 Captured locally\r\n");
                        } else if(line.equalsIgnoreCase("QUIT")) {writer.print("221 Bye\r\n");writer.flush();break;}
                        else writer.print("250 OK\r\n");
                        writer.flush();
                    }
                } catch(IOException e) {System.out.println("Mail capture connection ended: "+e.getClass().getSimpleName());}
            }
        }
    }
}
