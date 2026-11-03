package com.tbm.careerpathlearning.service.impl;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.net.URI;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.*;

/** All DDL and fixtures live in a transaction-private schema and are always rolled back. */
@EnabledIfEnvironmentVariable(named="APEX_PHASE2_POSTGRES_TEST",matches="true")
class CentralKpiResultMigrationPostgresTest {
    @TempDir Path compilerOutput;
    private static final String ACTOR="'00000000-0000-0000-0000-000000000001'";
    private static final String DELEGATE="'00000000-0000-0000-0000-000000000002'";
    private static final List<String> ORIGINAL_TABLES=List.of("staff","authority","role_authority",
            "annual_kpi_review_period","review_period_participant","review_checkpoint","kpi_plan","kpi",
            "kpi_scoring_definition","employee_kpi_assignment","kpi_plan_version","kpi_assessment_plan_version",
            "kpi_assessment","kpi_assessment_item","kpi_assessment_evidence");

    @ParameterizedTest @ValueSource(booleans={false,true})
    void preservesExistingDataAndAddsOnlyEmptyFoundation(boolean populated) throws Exception {
        isolated(c -> {
            fixture(c,populated);
            var before=new LinkedHashMap<String,String>();
            for(String table:ORIGINAL_TABLES) before.put(table,digest(c,table));
            migrate(c);
            for(var entry:before.entrySet()) assertEquals(entry.getValue(),digest(c,entry.getKey()),entry.getKey());
            for(String table:List.of("kpi_central_result","kpi_central_result_item","kpi_central_result_evidence",
                    "company_kpi_result_delegate","kpi_checkpoint_result")) assertEquals(0,count(c,table));
            assertEquals(4,number(c,"SELECT count(*) FROM annual_kpi_review_period WHERE assessment_model='LEGACY_ALL_LEVELS'"));
            assertEquals(populated?3:0,number(c,"SELECT count(*) FROM kpi_assessment WHERE assessment_model='LEGACY_ALL_LEVELS'"));
            assertEquals(1,number(c,"SELECT count(*) FROM authority WHERE name='CAN_ENTER_COMPANY_KPI_RESULTS'"));
            assertEquals(1,count(c,"role_authority"));
            sql(c,"INSERT INTO annual_kpi_review_period(id,status) VALUES(5,'DRAFT')");
            assertEquals(1,number(c,"SELECT count(*) FROM annual_kpi_review_period WHERE id=5 AND assessment_model='CENTRAL_RESULTS'"));
            sql(c,"UPDATE annual_kpi_review_period SET assessment_model='CENTRAL_RESULTS' WHERE id=3");
            reject(c,"23514","UPDATE annual_kpi_review_period SET assessment_model='CENTRAL_RESULTS' WHERE id=1");
            reject(c,"23514","UPDATE annual_kpi_review_period SET status='DRAFT',assessment_model='CENTRAL_RESULTS' WHERE id=2");
            reject(c,"23514","UPDATE annual_kpi_review_period SET assessment_model='CENTRAL_RESULTS' WHERE id=4");
            sql(c,"UPDATE annual_kpi_review_period SET status='OPEN' WHERE id=3");
            reject(c,"23514","UPDATE annual_kpi_review_period SET assessment_model='LEGACY_ALL_LEVELS' WHERE id=3");
        });
    }

    @ParameterizedTest @ValueSource(strings={"MONTHLY","QUARTERLY","ANNUALLY"})
    void preservesLegacyReviewScoreRequirementButAllowsCentralReviewBeforeResults(String frequency) throws Exception {
        isolated(c -> {
            fixture(c,false); migrate(c);
            int index=List.of("MONTHLY","QUARTERLY","ANNUALLY").indexOf(frequency)+1;
            assessment(c,10,index,index,1,frequency,null);
            reject(c,"23514",review(10));
            sql(c,review(10,"87.0000"));
            reject(c,"23514","UPDATE kpi_assessment SET checkpoint_score=NULL WHERE id=10");
            sql(c,"INSERT INTO annual_kpi_review_period(id,status) VALUES(5,'OPEN')");
            sql(c,"INSERT INTO review_period_participant VALUES(10,5,'"+frequency+"',7)");
            sql(c,"INSERT INTO review_checkpoint VALUES(10,5,'"+frequency+"')");
            reject(c,"23503",assessmentSql(20,10,10,5,frequency,null));
            assessment(c,20,10,10,5,frequency,"CENTRAL_RESULTS");
            sql(c,review(20));
            assertEquals(1,number(c,"SELECT count(*) FROM kpi_assessment WHERE id=20 AND status='REVIEWED' AND checkpoint_score IS NULL"));
            reject(c,"23514","UPDATE kpi_assessment SET checkpoint_score=100.0001 WHERE id=20");
            reject(c,"23514","UPDATE kpi_assessment SET reviewed_by=NULL WHERE id=20");
            reject(c,"23503","UPDATE kpi_assessment SET assessment_model='CENTRAL_RESULTS' WHERE id=10");
        });
    }

    @Test void enforcesCentralScopeCorrectionMetadataAndOneWorkingAndPublishedEdition() throws Exception {
        isolated(c -> {
            ready(c);
            draft(c,10,11,1,1,"MONTHLY","COMPANY",1,null);
            reject(c,"23505",draftSql(11,11,1,1,"MONTHLY","COMPANY",1,null));
            reject(c,"23503",draftSql(11,11,1,2,"MONTHLY","COMPANY",1,null));
            reject(c,"23503",draftSql(11,11,1,3,"QUARTERLY","COMPANY",1,null));
            reject(c,"23503",draftSql(11,11,1,2,"QUARTERLY","DEPARTMENT",1,null));
            reject(c,"23514",draftSql(11,13,1,2,"QUARTERLY","INDIVIDUAL",1,null));
            reject(c,"23514",draftSql(11,14,2,4,"MONTHLY","COMPANY",1,null));
            reject(c,"23514",draftSql(11,18,1,2,"QUARTERLY","COMPANY",1,null));
            reject(c,"23514",draftSql(11,11,1,1,"MONTHLY","COMPANY",2,10));
            publish(c,10);
            reject(c,"23514","UPDATE kpi_central_result SET status='DRAFT',published_by=NULL,published_at=NULL WHERE id=10");
            draft(c,20,11,1,1,"MONTHLY","COMPANY",2,10);
            reject(c,"23514","UPDATE kpi_central_result SET correction_reason='   ' WHERE id=20");
            reject(c,"23514","UPDATE kpi_central_result SET correction_reason=repeat('x',10001) WHERE id=20");
            reject(c,"23505",draftSql(21,11,1,1,"MONTHLY","COMPANY",3,10));
            reject(c,"23505",publicationSql(20));
            reject(c,"23514","UPDATE kpi_central_result SET checkpoint_id=2,review_frequency='QUARTERLY' WHERE id=20");
            sql(c,"UPDATE kpi_central_result SET status='SUPERSEDED',lock_version=lock_version+1 WHERE id=10");
            publish(c,20);
            draft(c,30,12,1,1,"MONTHLY","DEPARTMENT",1,null);
            publish(c,30);
            reject(c,"23503",draftSql(31,12,1,1,"MONTHLY","DEPARTMENT",3,20));
            assertEquals(2,number(c,"SELECT count(*) FROM kpi_central_result WHERE status='PUBLISHED'"));
            assertEquals(1,number(c,"SELECT count(*) FROM kpi_central_result WHERE status='SUPERSEDED'"));
        });
    }

    @Test void freezesPublishedItemsEvidenceAndAuditWhileAllowingIncompleteDrafts() throws Exception {
        isolated(c -> {
            ready(c); draft(c,10,11,1,1,"MONTHLY","COMPANY",1,null);
            item(c,1010,10,101,11);
            reject(c,"23503",itemSql(1011,10,102,11));
            reject(c,"23514","UPDATE kpi_central_result_item SET point=0 WHERE id=1010");
            reject(c,"23514","UPDATE kpi_central_result_item SET point=6 WHERE id=1010");
            reject(c,"23514","UPDATE kpi_central_result_item SET comment=repeat('x',10001) WHERE id=1010");
            reject(c,"23514","UPDATE kpi_central_result_item SET actual_achievement=repeat('x',10001) WHERE id=1010");
            sql(c,"UPDATE kpi_central_result_item SET actual_achievement='0',point=1,comment='Measured result' WHERE id=1010");
            evidence(c,5010,1010,"application/pdf",100);
            reject(c,"23514",evidenceSql(5011,1010,"image/svg+xml",100));
            reject(c,"23514",evidenceSql(5011,1010,"image/png",0));
            sql(c,"UPDATE kpi_central_result_evidence SET original_filename='revised.pdf' WHERE id=5010");
            publish(c,10);
            reject(c,"23514","UPDATE kpi_central_result SET correction_reason='Silent change' WHERE id=10");
            reject(c,"23514","DELETE FROM kpi_central_result WHERE id=10");
            reject(c,"23514","UPDATE kpi_central_result_item SET point=5 WHERE id=1010");
            reject(c,"23514","DELETE FROM kpi_central_result_item WHERE id=1010");
            reject(c,"23514",itemSql(1011,10,101,11));
            reject(c,"23514","UPDATE kpi_central_result_evidence SET original_filename='changed.pdf' WHERE id=5010");
            reject(c,"23514","DELETE FROM kpi_central_result_evidence WHERE id=5010");
            reject(c,"23514",evidenceSql(5011,1010,"image/png",100));
            draft(c,20,11,1,1,"MONTHLY","COMPANY",2,10);
            item(c,1020,20,101,11); evidence(c,5020,1020,"image/jpeg",100);
            reject(c,"23514","UPDATE kpi_central_result_item SET result_id=10 WHERE id=1020");
            reject(c,"23514","UPDATE kpi_central_result_item SET result_id=20 WHERE id=1010");
            reject(c,"23514","UPDATE kpi_central_result_evidence SET item_id=1010 WHERE id=5020");
            reject(c,"23514","UPDATE kpi_central_result_evidence SET item_id=1020 WHERE id=5010");
            sql(c,"DELETE FROM kpi_central_result_evidence WHERE id=5020");
            sql(c,"DELETE FROM kpi_central_result_item WHERE id=1020");
            sql(c,"UPDATE kpi_central_result SET status='SUPERSEDED' WHERE id=10");
            reject(c,"23514","UPDATE kpi_central_result SET status='PUBLISHED' WHERE id=10");
            reject(c,"23514","UPDATE kpi_central_result_item SET point=4 WHERE id=1010");
        });
    }

    @Test void retainsDelegateAssignmentAuditAndPermitsNewAssignmentAfterRevocation() throws Exception {
        isolated(c -> {
            fixture(c,false); migrate(c);
            String insert="INSERT INTO company_kpi_result_delegate(id,review_period_id,staff_id,assigned_by,assigned_at) VALUES";
            sql(c,insert+"(1,1,"+DELEGATE+","+ACTOR+",now())");
            reject(c,"23505",insert+"(2,1,"+DELEGATE+","+ACTOR+",now())");
            sql(c,insert+"(3,2,"+DELEGATE+","+ACTOR+",now())");
            reject(c,"23514","UPDATE company_kpi_result_delegate SET revoked_by="+ACTOR+" WHERE id=1");
            reject(c,"23514","UPDATE company_kpi_result_delegate SET assigned_by="+DELEGATE+" WHERE id=1");
            reject(c,"23514","DELETE FROM company_kpi_result_delegate WHERE id=1");
            sql(c,"UPDATE company_kpi_result_delegate SET revoked_by="+ACTOR+",revoked_at=assigned_at WHERE id=1");
            reject(c,"23514","UPDATE company_kpi_result_delegate SET revoked_by=NULL,revoked_at=NULL WHERE id=1");
            sql(c,insert+"(2,1,"+DELEGATE+","+ACTOR+",now())");
            assertEquals(3,count(c,"company_kpi_result_delegate"));
            assertEquals(1,number(c,"SELECT count(*) FROM company_kpi_result_delegate WHERE revoked_at IS NOT NULL"));
        });
    }

    @Test void enforcesAggregateOwnershipSourcesImmutableVersionBindingsAndMissingVersusZero() throws Exception {
        isolated(c -> {
            ready(c);
            draft(c,10,11,1,1,"MONTHLY","COMPANY",1,null);
            draft(c,20,12,1,1,"MONTHLY","DEPARTMENT",1,null);
            sql(c,"INSERT INTO kpi_checkpoint_result(id,participant_id,review_period_id,checkpoint_id,review_frequency) VALUES(1,1,1,1,'MONTHLY')");
            assertEquals(1,number(c,"SELECT count(*) FROM kpi_checkpoint_result WHERE score IS NULL AND calculated_at IS NULL AND calculation_history='[]'::jsonb"));
            reject(c,"23505","INSERT INTO kpi_checkpoint_result(participant_id,review_period_id,checkpoint_id,review_frequency) VALUES(1,1,1,'MONTHLY')");
            reject(c,"23503","INSERT INTO kpi_checkpoint_result(participant_id,review_period_id,checkpoint_id,review_frequency) VALUES(2,1,1,'QUARTERLY')");
            reject(c,"23514","INSERT INTO kpi_checkpoint_result(participant_id,review_period_id,checkpoint_id,review_frequency) VALUES(1,2,4,'MONTHLY')");
            reject(c,"23514","UPDATE kpi_checkpoint_result SET company_result_id=10 WHERE id=1");
            reject(c,"23514","UPDATE kpi_checkpoint_result SET department_version_id="+version(16)+" WHERE id=1");
            reject(c,"23514","UPDATE kpi_checkpoint_result SET company_version_id="+version(18)+" WHERE id=1");
            reject(c,"23503","UPDATE kpi_checkpoint_result SET company_version_id="+version(14)+" WHERE id=1");
            reject(c,"23503","UPDATE kpi_checkpoint_result SET company_version_id="+version(12)+" WHERE id=1");
            reject(c,"23514","UPDATE kpi_checkpoint_result SET individual_version_id="+version(17)+" WHERE id=1");
            reject(c,"23514","UPDATE kpi_checkpoint_result SET company_version_id="+version(11)+",company_result_id=10 WHERE id=1");
            sql(c,"UPDATE kpi_checkpoint_result SET company_version_id="+version(11)+",department_version_id="+version(12)
                    +",individual_version_id="+version(13)+" WHERE id=1");
            reject(c,"23514","UPDATE kpi_checkpoint_result SET company_version_id=NULL WHERE id=1");
            reject(c,"23514","UPDATE kpi_checkpoint_result SET department_version_id="+version(16)+" WHERE id=1");
            reject(c,"23514","UPDATE kpi_checkpoint_result SET checkpoint_id=6 WHERE id=1");
            publish(c,10); publish(c,20);
            sql(c,"UPDATE kpi_checkpoint_result SET company_result_id=10,department_result_id=20 WHERE id=1");
            reject(c,"23514","UPDATE kpi_checkpoint_result SET score=0 WHERE id=1");
            sql(c,"UPDATE kpi_checkpoint_result SET score=0,calculated_at=now(),calculation_history='[{\"score\":0}]'::jsonb WHERE id=1");
            assertEquals(1,number(c,"SELECT count(*) FROM kpi_checkpoint_result WHERE score=0 AND calculated_at IS NOT NULL"));
            reject(c,"23514","UPDATE kpi_checkpoint_result SET score=100.0001 WHERE id=1");
            reject(c,"23514","UPDATE kpi_checkpoint_result SET calculation_history='{}'::jsonb WHERE id=1");
            draft(c,30,11,1,1,"MONTHLY","COMPANY",2,10);
            sql(c,"UPDATE kpi_central_result SET status='SUPERSEDED' WHERE id=10");
            publish(c,30);
            sql(c,"UPDATE kpi_checkpoint_result SET company_result_id=30,score=77,calculated_at=now() WHERE id=1");
            draft(c,40,11,1,6,"MONTHLY","COMPANY",1,null); publish(c,40);
            reject(c,"23503","UPDATE kpi_checkpoint_result SET company_result_id=40 WHERE id=1");
            sql(c,"UPDATE kpi_checkpoint_result SET company_result_id=10 WHERE id=1");
        });
    }

    @Test void adoptsOnlyExactEmptyHibernateStructuresWithoutLosingLegacyRows() throws Exception {
        var compiler=ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler,"The runner preflight test requires a JDK");
        assertEquals(0,compiler.run(null,null,null,"-classpath",System.getProperty("java.class.path"),"-d",
                compilerOutput.toString(),Path.of("ApplyAnnualReviewPeriodFoundation.java").toAbsolutePath().toString()));
        try(var loader=new URLClassLoader(new java.net.URL[]{compilerOutput.toUri().toURL()},getClass().getClassLoader())) {
            var preflight=loader.loadClass("ApplyAnnualReviewPeriodFoundation")
                    .getDeclaredMethod("compatibleEmptyCentralFoundation",Connection.class,String.class);
            preflight.setAccessible(true);
            isolated(c -> {
                fixture(c,true); hibernateFoundation(c);
                String schema;
                try(var s=c.createStatement();var r=s.executeQuery("SELECT current_schema()")) {r.next();schema=r.getString(1);}
                assertEquals(true,preflight.invoke(null,c,schema));
                for(String incompatible:List.of(
                        "INSERT INTO kpi_central_result_item(result_id,kpi_id,plan_version_id) VALUES(1,1,1)",
                        "ALTER TABLE kpi_central_result ADD COLUMN unexpected TEXT",
                        "ALTER TABLE kpi_central_result ALTER COLUMN lock_version SET DEFAULT 0",
                        "ALTER TABLE kpi_central_result_item ADD CHECK (point>=0)",
                        "ALTER TABLE kpi_central_result_evidence ADD FOREIGN KEY(uploaded_by) REFERENCES staff(id)",
                        "CREATE INDEX unexpected_central_index ON kpi_central_result(level)",
                        "UPDATE kpi_assessment SET assessment_model='CENTRAL_RESULTS'",
                        "ALTER TABLE annual_kpi_review_period ADD COLUMN assessment_model VARCHAR(24)")) {
                    var savepoint=c.setSavepoint();
                    try {sql(c,incompatible);assertEquals(false,preflight.invoke(null,c,schema),incompatible);}
                    finally {c.rollback(savepoint);c.releaseSavepoint(savepoint);}
                }
                var before=new LinkedHashMap<String,String>();
                for(String table:ORIGINAL_TABLES) before.put(table,digest(c,table));
                migrate(c);
                for(var entry:before.entrySet()) assertEquals(entry.getValue(),digest(c,entry.getKey()),entry.getKey());
                assertEquals(3,number(c,"SELECT count(*) FROM kpi_assessment WHERE assessment_model='LEGACY_ALL_LEVELS'"));
                assertEquals(0,count(c,"kpi_checkpoint_result"));
                reject(c,"23514","UPDATE kpi_assessment SET checkpoint_score=NULL WHERE status='REVIEWED'");
                assertEquals(1,number(c,"SELECT count(*) FROM pg_constraint WHERE conrelid='kpi_central_result'::regclass AND conname='ck_central_result_level'"));
                assertEquals(1,number(c,"SELECT count(*) FROM pg_constraint WHERE conrelid='kpi_checkpoint_result'::regclass AND conname='fk_checkpoint_department_version'"));
            });
        }
    }

    private static void hibernateFoundation(Connection c) throws SQLException {
        sql(c,"ALTER TABLE kpi_assessment ADD COLUMN assessment_model VARCHAR(24) NOT NULL DEFAULT 'LEGACY_ALL_LEVELS'; "
                +"ALTER TABLE kpi_assessment ALTER COLUMN assessment_model DROP DEFAULT");
        sql(c,"CREATE TABLE kpi_central_result(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,review_period_id BIGINT NOT NULL,"
                +"checkpoint_id BIGINT NOT NULL,review_frequency VARCHAR(20) NOT NULL CHECK(review_frequency IN ('MONTHLY','QUARTERLY','ANNUALLY')),"
                +"plan_version_id BIGINT NOT NULL,level VARCHAR(20) NOT NULL CHECK(level IN ('COMPANY','DEPARTMENT','INDIVIDUAL')),"
                +"edition_number INTEGER NOT NULL,status VARCHAR(20) NOT NULL CHECK(status IN ('DRAFT','PUBLISHED','SUPERSEDED')),"
                +"previous_edition_id BIGINT,correction_reason TEXT,created_at TIMESTAMPTZ(6) NOT NULL,updated_at TIMESTAMPTZ(6) NOT NULL,"
                +"created_by UUID NOT NULL,updated_by UUID NOT NULL,published_at TIMESTAMPTZ(6),published_by UUID,lock_version BIGINT NOT NULL)");
        sql(c,"CREATE TABLE kpi_central_result_item(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,result_id BIGINT NOT NULL,"
                +"kpi_id BIGINT NOT NULL,plan_version_id BIGINT NOT NULL,actual_achievement TEXT,point INTEGER,comment TEXT)");
        sql(c,"CREATE TABLE kpi_central_result_evidence(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,item_id BIGINT NOT NULL,"
                +"storage_key VARCHAR(100) NOT NULL,original_filename VARCHAR(255) NOT NULL,content_type VARCHAR(100) NOT NULL,"
                +"size_bytes BIGINT NOT NULL,uploaded_by UUID NOT NULL,uploaded_at TIMESTAMPTZ(6) NOT NULL)");
        sql(c,"CREATE TABLE company_kpi_result_delegate(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,review_period_id BIGINT NOT NULL,"
                +"staff_id UUID NOT NULL,assigned_by UUID NOT NULL,assigned_at TIMESTAMPTZ(6) NOT NULL,revoked_by UUID,revoked_at TIMESTAMPTZ(6))");
        sql(c,"CREATE TABLE kpi_checkpoint_result(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,participant_id BIGINT NOT NULL,"
                +"review_period_id BIGINT NOT NULL,checkpoint_id BIGINT NOT NULL,review_frequency VARCHAR(20) NOT NULL "
                +"CHECK(review_frequency IN ('MONTHLY','QUARTERLY','ANNUALLY')),company_version_id BIGINT,department_version_id BIGINT,"
                +"individual_version_id BIGINT,company_result_id BIGINT,department_result_id BIGINT,score NUMERIC(7,4),"
                +"calculated_at TIMESTAMPTZ(6),calculation_history JSONB NOT NULL,lock_version BIGINT NOT NULL)");
    }

    private static void isolated(DatabaseWork work) throws Exception {
        var env=Dotenv.configure().directory(".").load();
        String url=env.get("DB_URL");
        URI target=URI.create(url.substring(5));
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(target.getHost()));
        assertNull(target.getQuery(),"Test URL must not override the private schema search path");
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                sql(c,"SET LOCAL lock_timeout='5s'");
                sql(c,"SET LOCAL statement_timeout='30s'");
                String schema="apex_central_"+UUID.randomUUID().toString().replace("-","");
                sql(c,"CREATE SCHEMA "+schema);
                sql(c,"SET LOCAL search_path TO "+schema);
                work.run(c);
            } finally { c.rollback(); }
        }
    }

    private static void fixture(Connection c,boolean populated) throws Exception {
        sql(c,"CREATE TABLE staff(id UUID PRIMARY KEY); INSERT INTO staff VALUES("+ACTOR+"),("+DELEGATE+")");
        sql(c,"CREATE TABLE authority(id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,name VARCHAR(100) UNIQUE,description_key TEXT,label_key TEXT)");
        sql(c,"INSERT INTO authority(name) VALUES('ROLE_USER')");
        sql(c,"CREATE TABLE role_authority(role_id BIGINT,authority_id BIGINT REFERENCES authority(id)); INSERT INTO role_authority VALUES(1,1)");
        sql(c,"CREATE TABLE annual_kpi_review_period(id BIGINT PRIMARY KEY,status VARCHAR(20) NOT NULL,start_date DATE,end_date DATE,name TEXT)");
        sql(c,"INSERT INTO annual_kpi_review_period VALUES(1,'OPEN','2026-10-09','2026-12-31','Existing period'),"
                +"(2,'CLOSED','2025-01-01','2025-12-31','Closed'),(3,'DRAFT',NULL,NULL,'Draft'),(4,'UPCOMING','2027-01-01','2027-12-31','Upcoming')");
        sql(c,"CREATE TABLE review_period_participant(id BIGINT PRIMARY KEY,review_period_id BIGINT REFERENCES annual_kpi_review_period(id),review_frequency VARCHAR(20),department_id BIGINT)");
        sql(c,"CREATE TABLE review_checkpoint(id BIGINT PRIMARY KEY,review_period_id BIGINT REFERENCES annual_kpi_review_period(id),review_frequency VARCHAR(20))");
        sql(c,"INSERT INTO review_period_participant VALUES(1,1,'MONTHLY',7),(2,1,'QUARTERLY',7),(3,1,'ANNUALLY',7),(4,2,'MONTHLY',7),(5,3,'MONTHLY',7)");
        sql(c,"INSERT INTO review_checkpoint VALUES(1,1,'MONTHLY'),(2,1,'QUARTERLY'),(3,1,'ANNUALLY'),(4,2,'MONTHLY'),(5,3,'MONTHLY'),(6,1,'MONTHLY')");
        sql(c,"CREATE TABLE kpi_plan(id BIGINT PRIMARY KEY,review_period_id BIGINT REFERENCES annual_kpi_review_period(id),level VARCHAR(20),status VARCHAR(20),"
                +"department_id BIGINT,owner_participant_id BIGINT,created_at TIMESTAMPTZ,updated_at TIMESTAMPTZ,created_by UUID,updated_by UUID,"
                +"published_at TIMESTAMPTZ,reviewed_at TIMESTAMPTZ,reviewed_by UUID,latest_return_reason TEXT,latest_returned_at TIMESTAMPTZ,latest_returned_by UUID,latest_return_items JSONB)");
        sql(c,"CREATE TABLE kpi(id BIGINT PRIMARY KEY,plan_id BIGINT,review_period_id BIGINT,target TEXT)");
        sql(c,"CREATE TABLE kpi_scoring_definition(kpi_id BIGINT REFERENCES kpi(id),point INTEGER,definition TEXT,PRIMARY KEY(kpi_id,point))");
        sql(c,"CREATE TABLE employee_kpi_assignment(id BIGINT PRIMARY KEY,kpi_id BIGINT,participant_id BIGINT,review_period_id BIGINT)");
        migration(c,"V39__kpi_self_assessment.sql");
        if(populated) {
            sql(c,"INSERT INTO kpi_plan(id,review_period_id,level,status,department_id,owner_participant_id,created_at,updated_at,created_by,updated_by,published_at,reviewed_at) "
                    +"SELECT id,period,level,status,department,owner,now(),now(),"+ACTOR+","+ACTOR+","
                    +"CASE WHEN status='PUBLISHED' THEN now() END,CASE WHEN status='APPROVED' THEN now() END FROM (VALUES"
                    +"(11,1,'COMPANY','PUBLISHED',NULL::bigint,NULL::bigint),(12,1,'DEPARTMENT','APPROVED',7,NULL),"
                    +"(13,1,'INDIVIDUAL','APPROVED',NULL,1),(14,2,'COMPANY','PUBLISHED',NULL,NULL),"
                    +"(15,3,'COMPANY','PUBLISHED',NULL,NULL),(16,1,'DEPARTMENT','APPROVED',8,NULL),"
                    +"(17,1,'INDIVIDUAL','APPROVED',NULL,2),(18,1,'COMPANY','DRAFT',NULL,NULL)) x(id,period,level,status,department,owner)");
            sql(c,"INSERT INTO kpi VALUES(101,11,1,'Company target'),(102,12,1,'Department target'),(103,13,1,'Individual target')");
            sql(c,"INSERT INTO kpi_scoring_definition VALUES(101,5,'Original scoring definition')");
            sql(c,"INSERT INTO employee_kpi_assignment VALUES(201,101,1,1),(202,102,1,1),(203,103,1,1),(204,103,2,1),(205,103,3,1)");
            assessment(c,301,1,1,1,"MONTHLY",null);
            assessment(c,302,2,2,1,"QUARTERLY",null);
            assessment(c,303,3,3,1,"ANNUALLY",null);
            sql(c,"UPDATE kpi_assessment SET status='PENDING_REVIEW',submitted_at=now(),submitted_by="+ACTOR
                    +",submitted_to_superior_id="+ACTOR+",submitted_late=false WHERE id=302");
            sql(c,review(303,"87.0000"));
            sql(c,"INSERT INTO kpi_assessment_item(id,assessment_id,assignment_id,participant_id,review_period_id,self_point,superior_point) VALUES"
                    +"(401,301,201,1,1,3,NULL),(402,302,204,2,1,4,NULL),(403,303,205,3,1,5,4)");
            sql(c,"INSERT INTO kpi_assessment_evidence(item_id,storage_key,original_filename,content_type,size_bytes,uploaded_by,uploaded_at) "
                    +"VALUES(403,'original-evidence-key','original.png','image/png',123,"+ACTOR+",now())");
        }
        migration(c,"V44__controlled_kpi_plan_revisions.sql");
        migration(c,"V45__individual_kpi_revisions.sql");
    }

    private static void ready(Connection c) throws Exception {
        fixture(c,true);
        sql(c,"UPDATE annual_kpi_review_period SET status='DRAFT' WHERE id=1");
        migrate(c);
        // Only disposable fixture rows are converted, after removing no live records.
        sql(c,"DELETE FROM kpi_assessment_evidence; DELETE FROM kpi_assessment_item; DELETE FROM kpi_assessment_plan_version; DELETE FROM kpi_assessment");
        sql(c,"UPDATE annual_kpi_review_period SET assessment_model='CENTRAL_RESULTS' WHERE id=1");
        sql(c,"UPDATE annual_kpi_review_period SET status='OPEN' WHERE id=1");
    }

    private static void migrate(Connection c) throws Exception { migration(c,"V46__central_kpi_result_foundation.sql"); }
    private static void migration(Connection c,String file) throws Exception {
        sql(c,new ClassPathResource("db/migration/annual-kpi/"+file).getContentAsString(StandardCharsets.UTF_8));
    }
    private static String version(int plan) { return "(SELECT current_version_id FROM kpi_plan WHERE id="+plan+")"; }
    private static String review(int id) {
        return review(id,null);
    }
    private static String review(int id,String score) {
        return "UPDATE kpi_assessment SET status='REVIEWED',submitted_at=now(),submitted_by="+ACTOR
                +",submitted_to_superior_id="+ACTOR+",submitted_late=false,reviewed_at=now(),reviewed_by="+ACTOR+",reviewed_late=false"
                +(score==null?"":",checkpoint_score="+score)+" WHERE id="+id;
    }
    private static String assessmentSql(int id,int participant,int checkpoint,int period,String frequency,String model) {
        return "INSERT INTO kpi_assessment(id,participant_id,checkpoint_id,review_period_id,review_frequency,created_at,updated_at,created_by,updated_by"
                +(model==null?"":",assessment_model")+") VALUES("+id+","+participant+","+checkpoint+","+period+",'"+frequency+"',now(),now(),"
                +ACTOR+","+ACTOR+(model==null?"":",'"+model+"'")+")";
    }
    private static void assessment(Connection c,int id,int participant,int checkpoint,int period,String frequency,String model) throws SQLException {
        sql(c,assessmentSql(id,participant,checkpoint,period,frequency,model));
    }
    private static String draftSql(int id,int plan,int period,int checkpoint,String frequency,String level,int edition,Integer previous) {
        return "INSERT INTO kpi_central_result(id,review_period_id,checkpoint_id,review_frequency,plan_version_id,level,edition_number,previous_edition_id,"
                +"correction_reason,created_at,updated_at,created_by,updated_by) VALUES("+id+","+period+","+checkpoint+",'"+frequency+"',"+version(plan)
                +",'"+level+"',"+edition+","+(previous==null?"NULL":previous)+","+(previous==null?"NULL":"'Correct measurement'")
                +",now(),now(),"+ACTOR+","+ACTOR+")";
    }
    private static void draft(Connection c,int id,int plan,int period,int checkpoint,String frequency,String level,int edition,Integer previous) throws SQLException {
        sql(c,draftSql(id,plan,period,checkpoint,frequency,level,edition,previous));
    }
    private static String itemSql(int id,int result,int kpi,int plan) {
        return "INSERT INTO kpi_central_result_item(id,result_id,kpi_id,plan_version_id) VALUES("+id+","+result+","+kpi+","+version(plan)+")";
    }
    private static void item(Connection c,int id,int result,int kpi,int plan) throws SQLException { sql(c,itemSql(id,result,kpi,plan)); }
    private static String evidenceSql(int id,int item,String type,long size) {
        return "INSERT INTO kpi_central_result_evidence(id,item_id,storage_key,original_filename,content_type,size_bytes,uploaded_by,uploaded_at) VALUES("
                +id+","+item+",'private-central-"+id+"','evidence.pdf','"+type+"',"+size+","+ACTOR+",now())";
    }
    private static void evidence(Connection c,int id,int item,String type,long size) throws SQLException { sql(c,evidenceSql(id,item,type,size)); }
    private static String publicationSql(int id) {
        return "UPDATE kpi_central_result SET status='PUBLISHED',published_at=now(),published_by="+ACTOR+" WHERE id="+id;
    }
    private static void publish(Connection c,int id) throws SQLException { sql(c,publicationSql(id)); }
    private static String digest(Connection c,String table) throws SQLException {
        String row=List.of("annual_kpi_review_period","kpi_assessment").contains(table)?"to_jsonb(t)-'assessment_model'":"to_jsonb(t)";
        String filter=table.equals("authority")?" WHERE name<>'CAN_ENTER_COMPANY_KPI_RESULTS'":"";
        try(var s=c.createStatement();var r=s.executeQuery("SELECT coalesce(jsonb_agg("+row+" ORDER BY ("+row+")::text),'[]'::jsonb)::text FROM "+table+" t"+filter)) {
            r.next(); return r.getString(1);
        }
    }
    private static long count(Connection c,String table) throws SQLException { return number(c,"SELECT count(*) FROM "+table); }
    private static long number(Connection c,String query) throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery(query)) { r.next(); return r.getLong(1); }
    }
    private static void sql(Connection c,String query) throws SQLException { try(var s=c.createStatement()) { s.execute(query); } }
    private static void reject(Connection c,String state,String query) throws SQLException {
        var savepoint=c.setSavepoint();
        try { assertEquals(state,assertThrows(SQLException.class,()->sql(c,query)).getSQLState(),query); }
        finally { c.rollback(savepoint); c.releaseSavepoint(savepoint); }
    }
    @FunctionalInterface private interface DatabaseWork { void run(Connection c) throws Exception; }
}
