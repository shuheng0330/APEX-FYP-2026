package com.tbm.careerpathlearning.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.persistence.Entity;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="APEX_PHASE4_POSTGRES_TEST",matches="true")
class AnnualAppraisalMigrationPostgresTest {
    static final UUID EMPLOYEE=UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID SUPERIOR=UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID HR=UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test void additiveMigrationPreservesLegacyAndAssessmentRowsAndDoesNotGrantRoles() throws Exception {
        isolated((c,env,schema)->{
            fixture(c);String before=text(c,"SELECT jsonb_agg(to_jsonb(t))::text FROM appraisal_record t");
            sql(c,migration());
            assertEquals(before,text(c,"SELECT jsonb_agg(to_jsonb(t))::text FROM appraisal_record t"));
            assertEquals("80",text(c,"SELECT score::text FROM preserved_assessment WHERE id=1"));
            assertEquals(0,count(c,"SELECT count(*) FROM annual_appraisal"));
            assertEquals(0,count(c,"SELECT count(*) FROM role_authority"));
            assertEquals(2,count(c,"SELECT count(*) FROM authority WHERE name IN ('CAN_SUBMIT_ANNUAL_APPRAISAL','CAN_REVIEW_ANNUAL_APPRAISAL')"));
        });
    }
    @Test void oneEmployeePeriodAndCorrectParticipantOwnershipAreEnforced() throws Exception {
        isolated((c,env,schema)->{
            fixture(c);sql(c,migration());draft(c,1,7,1,EMPLOYEE);
            reject(c,"23505",()->draft(c,2,7,1,EMPLOYEE));
            sql(c,"INSERT INTO review_period_participant VALUES (8,2,'"+EMPLOYEE+"')");
            reject(c,"23503",()->draft(c,2,8,99,EMPLOYEE));
            reject(c,"23503",()->draft(c,2,8,2,SUPERIOR));
            reject(c,"23514",()->sql(c,"INSERT INTO annual_appraisal(participant_id,review_period_id,employee_id,superior_id,status,created_at,updated_at,created_by,updated_by) "
                    +"VALUES (8,2,'"+EMPLOYEE+"','"+SUPERIOR+"','RETURNED',now(),now(),'"+SUPERIOR+"','"+SUPERIOR+"')"));
            reject(c,"23514",()->sql(c,"UPDATE annual_appraisal SET superior_id='"+HR+"' WHERE id=1"));
        });
    }
    @Test void pendingAndApprovedContentAndAppendOnlyActivityAreProtected() throws Exception {
        isolated((c,env,schema)->{
            fixture(c);sql(c,migration());draft(c,1,7,1,EMPLOYEE);submit(c);
            reject(c,"23514",()->sql(c,"UPDATE annual_appraisal SET justification='Changed' WHERE id=1"));
            reject(c,"23514",()->sql(c,"UPDATE annual_appraisal SET status='DRAFT' WHERE id=1"));
            sql(c,"INSERT INTO annual_appraisal_activity(appraisal_id,action,actor_id,occurred_at,snapshot) "
                    +"SELECT id,'SUBMITTED',superior_id,now(),snapshot FROM annual_appraisal WHERE id=1");
            reject(c,"23514",()->sql(c,"UPDATE annual_appraisal_activity SET reason='Changed'"));
            reject(c,"23514",()->sql(c,"DELETE FROM annual_appraisal_activity"));
            reject(c,"23514",()->sql(c,"UPDATE annual_appraisal SET status='APPROVED',final_decision='PROMOTION',reviewed_by=superior_id,reviewed_at=now(),reviewed_late=false WHERE id=1"));
            sql(c,"UPDATE annual_appraisal SET status='APPROVED',final_decision=recommendation,reviewed_by='"+HR+"',reviewed_at=now(),reviewed_late=false WHERE id=1");
            reject(c,"23514",()->sql(c,"UPDATE annual_appraisal SET justification='Changed' WHERE id=1"));
            reject(c,"23514",()->sql(c,"DELETE FROM annual_appraisal WHERE id=1"));
        });
    }
    @Test void overrideRequiresDifferentDecisionReasonAndSubmittedScoreCannotBeMissing() throws Exception {
        isolated((c,env,schema)->{
            fixture(c);sql(c,migration());draft(c,1,7,1,EMPLOYEE);
            reject(c,"23514",()->sql(c,"UPDATE annual_appraisal SET status='PENDING_REVIEW',recommendation='PROMOTION',justification='Reason',submitted_at=now(),submitted_late=false,snapshot='{}'::jsonb WHERE id=1"));
            submit(c);
            reject(c,"23514",()->sql(c,"UPDATE annual_appraisal SET status='APPROVED',final_decision='BOTH',reviewed_by='"+HR+"',reviewed_at=now(),reviewed_late=false WHERE id=1"));
            sql(c,"UPDATE annual_appraisal SET status='APPROVED',final_decision='BOTH',override_reason='Reviewed evidence',reviewed_by='"+HR+"',reviewed_at=now(),reviewed_late=false WHERE id=1");
        });
    }
    @Test void returnedAppraisalsAllowIncompleteDraftEditsButKeepSubmittedHistory() throws Exception {
        isolated((c,env,schema)->{
            fixture(c);sql(c,migration());draft(c,1,7,1,EMPLOYEE);submit(c);
            sql(c,"UPDATE annual_appraisal SET status='RETURNED',latest_return_reason='Clarify',returned_recommendation=recommendation,returned_justification=justification WHERE id=1");
            sql(c,"UPDATE annual_appraisal SET recommendation=NULL,justification=NULL WHERE id=1");
            assertEquals(1,count(c,"SELECT count(*) FROM annual_appraisal WHERE recommendation IS NULL AND snapshot IS NOT NULL"));
        });
    }
    @Test void jpaRepositoriesRoundTripStructuredSnapshotsAndOptimisticVersions() throws Exception {
        isolated((c,env,schema)->{
            fixture(c);sql(c,migration());
            var config=new Configuration();config.setProperty("hibernate.connection.url",env.get("DB_URL"));
            config.setProperty("hibernate.connection.username",env.get("DB_USER"));config.setProperty("hibernate.connection.password",env.get("DB_PASS"));
            config.setProperty("hibernate.hbm2ddl.auto","none");config.setProperty("hibernate.default_schema",schema);
            config.setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
            var scanner=new ClassPathScanningCandidateComponentProvider(false);scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for(var candidate:scanner.findCandidateComponents("com.tbm.careerpathlearning.model"))config.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
            try(var sf=config.buildSessionFactory();var em=sf.withOptions().connection(c).openSession()) {
                em.beginTransaction();
                var factory=new JpaRepositoryFactory(em);var repository=factory.getRepository(AnnualAppraisalRepository.class);
                var activities=factory.getRepository(AnnualAppraisalActivityRepository.class);
                var appraisal=new AnnualAppraisal();appraisal.setParticipant(em.getReference(ReviewPeriodParticipant.class,7L));
                appraisal.setReviewPeriodId(1L);appraisal.setEmployeeId(EMPLOYEE);appraisal.setSuperiorId(SUPERIOR);
                appraisal.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));appraisal.setUpdatedAt(appraisal.getCreatedAt());
                appraisal.setCreatedBy(SUPERIOR);appraisal.setUpdatedBy(SUPERIOR);
                repository.saveAndFlush(appraisal);Long id=appraisal.getId();
                appraisal.setRecommendation(AnnualAppraisalRecommendation.NO_RECOMMENDATION);appraisal.setJustification("No change recommended");
                appraisal.setSnapshot(new AnnualAppraisalSnapshotDto(7L,1L,EMPLOYEE,ReviewFrequency.ANNUALLY,AnnualKpiConsolidationMethod.AVERAGE,
                        new BigDecimal("50"),new BigDecimal("50"),new BigDecimal("80"),new BigDecimal("90"),new BigDecimal("85.0000"),List.of(),null));
                appraisal.setSubmittedAt(appraisal.getCreatedAt());appraisal.setSubmittedLate(false);appraisal.setStatus(AppraisalStatus.PENDING_REVIEW);
                repository.saveAndFlush(appraisal);
                var event=new AnnualAppraisalActivity();event.setAppraisalId(id);event.setAction("SUBMITTED");event.setActorId(SUPERIOR);
                event.setOccurredAt(appraisal.getSubmittedAt());event.setSnapshot(appraisal.getSnapshot());activities.saveAndFlush(event);
                em.clear();var loaded=repository.findByParticipantId(7L).orElseThrow();
                assertEquals(AnnualAppraisalRecommendation.NO_RECOMMENDATION,loaded.getRecommendation());assertEquals(1,loaded.getLockVersion());
                assertEquals(0,new BigDecimal("85").compareTo(loaded.getSnapshot().finalScore()));
                assertEquals(1,repository.findAllByReviewPeriodIdOrderByUpdatedAtDescIdDesc(1L).size());
                assertEquals(1,activities.findAllByAppraisalIdOrderByIdAsc(id).size());
                var json=new ObjectMapper().registerModule(new JavaTimeModule());
                assertEquals(json.valueToTree(loaded.getSnapshot()),json.valueToTree(activities.findAllByAppraisalIdOrderByIdAsc(id).get(0).getSnapshot()));
                em.getTransaction().rollback();
            }
        });
    }

    private void fixture(Connection c) throws Exception {
        sql(c,"CREATE TABLE staff(id UUID PRIMARY KEY)");
        sql(c,"INSERT INTO staff VALUES ('"+EMPLOYEE+"'),('"+SUPERIOR+"'),('"+HR+"')");
        sql(c,"CREATE TABLE review_period_participant(id BIGINT PRIMARY KEY,review_period_id BIGINT NOT NULL,staff_id UUID NOT NULL)");
        sql(c,"INSERT INTO review_period_participant VALUES (7,1,'"+EMPLOYEE+"')");
        sql(c,"CREATE TABLE authority(name TEXT PRIMARY KEY,description_key TEXT,label_key TEXT,CONSTRAINT authority_name_check CHECK(name='ROLE_USER'))");
        sql(c,"INSERT INTO authority VALUES ('ROLE_USER','role','role')");sql(c,"CREATE TABLE role_authority(role_id BIGINT,authority_id BIGINT)");
        sql(c,"CREATE TABLE appraisal_record(id BIGINT PRIMARY KEY,payload JSONB)");sql(c,"INSERT INTO appraisal_record VALUES(1,'{\"decision\":\"PROMOTION\"}')");
        sql(c,"CREATE TABLE preserved_assessment(id BIGINT PRIMARY KEY,score NUMERIC)");sql(c,"INSERT INTO preserved_assessment VALUES(1,80)");
    }
    private void draft(Connection c,int id,int participant,int period,UUID employee) throws Exception {
        sql(c,"INSERT INTO annual_appraisal(id,participant_id,review_period_id,employee_id,superior_id,created_at,updated_at,created_by,updated_by) "
                +"VALUES ("+id+","+participant+","+period+",'"+employee+"','"+SUPERIOR+"',now(),now(),'"+SUPERIOR+"','"+SUPERIOR+"')");
    }
    private void submit(Connection c) throws Exception {
        sql(c,"UPDATE annual_appraisal SET status='PENDING_REVIEW',recommendation='PROMOTION',justification='Reason',submitted_at=now(),submitted_late=false,snapshot='{\"finalScore\":80}'::jsonb WHERE id=1");
    }
    private String migration() throws Exception {return new ClassPathResource("db/migration/annual-kpi/V48__annual_staff_appraisal.sql").getContentAsString(StandardCharsets.UTF_8);}
    private void sql(Connection c,String sql) throws Exception {try(var s=c.createStatement()){s.execute(sql);}}
    private String text(Connection c,String sql) throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private long count(Connection c,String sql) throws Exception {return Long.parseLong(text(c,sql));}
    private void reject(Connection c,String state,SqlWork work) throws Exception {
        var point=c.setSavepoint();assertEquals(state,assertThrows(SQLException.class,work::run).getSQLState());c.rollback(point);
    }
    private void isolated(DatabaseWork work) throws Exception {
        var env=Dotenv.configure().directory(".").load();
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(env.get("DB_URL").substring(5)).getHost()));
        try(var c=DriverManager.getConnection(env.get("DB_URL"),env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            var before=publicDigests(c);
            try {var schema="annual_appraisal_test_"+UUID.randomUUID().toString().replace("-","");
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);work.run(c,env,schema);
            } finally {c.rollback();}
            assertEquals(before,publicDigests(c),"Public period, assessment, permission and legacy appraisal rows must remain unchanged");
        }
    }
    private Map<String,String> publicDigests(Connection c) throws Exception {
        var result=new LinkedHashMap<String,String>();
        for(var table:List.of("annual_kpi_review_period","review_period_participant","kpi_assessment","kpi_assessment_item",
                "attitude_assessment","attitude_assessment_item","kpi_central_result","kpi_checkpoint_result",
                "appraisal_record","authority","role_authority"))
            result.put(table,text(c,"SELECT md5(COALESCE(string_agg(row_data,E'\\n' ORDER BY row_data),'')) "
                    +"FROM (SELECT row_to_json(t)::text row_data FROM public."+table+" t) rows"));
        return result;
    }
    @FunctionalInterface interface SqlWork {void run() throws Exception;}
    @FunctionalInterface interface DatabaseWork {void run(Connection c,Dotenv env,String schema) throws Exception;}
}
