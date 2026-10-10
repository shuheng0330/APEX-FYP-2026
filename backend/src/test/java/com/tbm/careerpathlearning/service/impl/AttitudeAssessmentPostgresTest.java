package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.EmailService;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.persistence.Entity;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.tbm.careerpathlearning.service.impl.AttitudeConfigurationPostgresTest.*;

@EnabledIfEnvironmentVariable(named="APEX_PHASE3_POSTGRES_TEST",matches="true")
class AttitudeAssessmentPostgresTest {
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void reviewMigrationPreservesExistingDataAndAddsNoRoleGrants(boolean populated) throws Exception {
        try(var c=connection()) {
            try {
                var schema="attitude_review_sql_"+UUID.randomUUID().toString().replace("-","");
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                sql(c,"CREATE TABLE authority (LIKE public.authority INCLUDING ALL)");
                sql(c,"CREATE TABLE role_authority (LIKE public.role_authority INCLUDING ALL)");
                if(populated) {
                    sql(c,"INSERT INTO authority SELECT * FROM public.authority WHERE name<>'CAN_REVIEW_ATTITUDE_EVALUATION'");
                    sql(c,"INSERT INTO role_authority SELECT r.* FROM public.role_authority r JOIN authority a ON a.id=r.authority_id");
                }
                sql(c,"ALTER TABLE authority ALTER COLUMN id RESTART WITH "+scalar(c,"SELECT coalesce(max(id),0)+1 FROM authority"));
                String grants=text(c,"SELECT md5(coalesce(string_agg(to_jsonb(r)::text,',' ORDER BY role_id,authority_id),'')) FROM role_authority r");
                String authorities=text(c,"SELECT md5(coalesce(string_agg(to_jsonb(a)::text,',' ORDER BY id),'')) FROM authority a");
                String periods=text(c,"SELECT md5(coalesce(string_agg(to_jsonb(p)::text,',' ORDER BY id),'')) FROM public.annual_kpi_review_period p");
                String assessments=text(c,"SELECT md5(coalesce(string_agg(to_jsonb(a)::text,',' ORDER BY id),'')) FROM public.attitude_assessment a");
                sql(c,new ClassPathResource("db/migration/annual-kpi/V42__superior_attitude_evaluation_permission.sql").getContentAsString(StandardCharsets.UTF_8));
                assertEquals(1,scalar(c,"SELECT count(*) FROM authority WHERE name='CAN_REVIEW_ATTITUDE_EVALUATION'"));
                assertEquals(authorities,text(c,"SELECT md5(coalesce(string_agg(to_jsonb(a)::text,',' ORDER BY id),'')) FROM authority a WHERE name<>'CAN_REVIEW_ATTITUDE_EVALUATION'"));
                assertEquals(grants,text(c,"SELECT md5(coalesce(string_agg(to_jsonb(r)::text,',' ORDER BY role_id,authority_id),'')) FROM role_authority r"));
                assertEquals(periods,text(c,"SELECT md5(coalesce(string_agg(to_jsonb(p)::text,',' ORDER BY id),'')) FROM public.annual_kpi_review_period p"));
                assertEquals(assessments,text(c,"SELECT md5(coalesce(string_agg(to_jsonb(a)::text,',' ORDER BY id),'')) FROM public.attitude_assessment a"));
                reject(c,"23514","INSERT INTO authority(name,description_key,label_key) VALUES('UNKNOWN_PERMISSION','unknown','unknown')");
            } finally {c.rollback();}
        }
    }
    String migration() throws Exception {
        return new ClassPathResource("db/migration/annual-kpi/V41__attitude_self_assessment.sql").getContentAsString(StandardCharsets.UTF_8);
    }
    Connection connection() throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"));c.setAutoCommit(false);return c;
    }
    @Test void migrationSupportsEmptyAndPopulatedFixturesAndEnforcesHistoricalScope() throws Exception {
        try(var c=connection()) {
            try {
                String schema="attitude_assessment_sql_"+UUID.randomUUID().toString().replace("-","");
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                sql(c,"CREATE TABLE staff(id uuid PRIMARY KEY)");
                sql(c,"CREATE TABLE attitude_configuration(id bigint PRIMARY KEY)");
                sql(c,"CREATE TABLE annual_kpi_review_period(id bigint PRIMARY KEY,attitude_configuration_id bigint)");
                sql(c,"CREATE TABLE review_period_participant(id bigint PRIMARY KEY,review_period_id bigint,UNIQUE(id,review_period_id))");
                sql(c,"CREATE TABLE attitude_criterion(id bigint PRIMARY KEY,configuration_id bigint)");
                var before=c.setSavepoint();sql(c,migration());assertEquals(0,scalar(c,"SELECT count(*) FROM attitude_assessment"));c.rollback(before);
                sql(c,"INSERT INTO staff VALUES('00000000-0000-0000-0000-000000000001')");
                sql(c,"INSERT INTO attitude_configuration VALUES(1),(2)");
                sql(c,"INSERT INTO annual_kpi_review_period VALUES(1,1),(2,2),(3,null)");
                sql(c,"INSERT INTO review_period_participant VALUES(1,1),(2,2),(3,1),(4,3)");
                sql(c,"INSERT INTO attitude_criterion VALUES(1,1),(2,2)");sql(c,migration());
                assertEquals(3,scalar(c,"SELECT count(*) FROM annual_kpi_review_period"));assertEquals(4,scalar(c,"SELECT count(*) FROM review_period_participant"));
                assertEquals(0,scalar(c,"SELECT count(*) FROM attitude_assessment"));
                String insert="INSERT INTO attitude_assessment(id,participant_id,review_period_id,configuration_id,evaluation_format,created_at,updated_at,created_by,updated_by) VALUES";
                String metadata=",'SALES',now(),now(),'00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')";
                sql(c,insert+"(1,1,1,1"+metadata);
                reject(c,"23505",insert+"(2,1,1,1"+metadata);
                reject(c,"23503",insert+"(2,2,1,1"+metadata);
                reject(c,"23503",insert+"(2,3,1,2"+metadata);
                reject(c,"23503",insert+"(2,4,3,1"+metadata);
                sql(c,"INSERT INTO attitude_assessment_item(id,assessment_id,criterion_id,configuration_id,self_point) VALUES(1,1,1,1,4)");
                reject(c,"23503","INSERT INTO attitude_assessment_item(id,assessment_id,criterion_id,configuration_id) VALUES(2,1,2,1)");
                reject(c,"23503","INSERT INTO attitude_assessment_item(id,assessment_id,criterion_id,configuration_id) VALUES(2,1,2,2)");
                reject(c,"23505","INSERT INTO attitude_assessment_item(id,assessment_id,criterion_id,configuration_id) VALUES(2,1,1,1)");
                reject(c,"23514","UPDATE attitude_assessment_item SET self_point=0");
                reject(c,"23514","UPDATE attitude_assessment_item SET self_point=6");
                reject(c,"23514","UPDATE attitude_assessment_item SET self_comment=repeat('x',10001)");
                reject(c,"23514","UPDATE attitude_assessment SET status='PENDING_REVIEW'");
                reject(c,"23514","UPDATE attitude_assessment SET attitude_score=80");
                reject(c,"23503","UPDATE annual_kpi_review_period SET attitude_configuration_id=2 WHERE id=1");
                reject(c,"23503","DELETE FROM attitude_criterion WHERE id=1");
                sql(c,"UPDATE attitude_assessment SET status='PENDING_REVIEW',submitted_at=now(),submitted_by=created_by,submitted_to_superior_id=created_by,submitted_late=false");
                reject(c,"23514","UPDATE attitude_assessment SET status='REVIEWED'");
                sql(c,"UPDATE attitude_assessment SET status='REVIEWED',reviewed_at=now(),reviewed_by=created_by,reviewed_late=false,attitude_score=80");
                reject(c,"23514","UPDATE attitude_assessment SET attitude_score=100.0001");
                reject(c,"23514","UPDATE attitude_assessment SET attitude_score=null");
            } finally {c.rollback();}
        }
    }
    @Test void realJpaDraftSubmissionRollbackAndImmutableConfigurationUseNoLiveFixtureWrites() throws Exception {
        var env=Dotenv.configure().directory(".").load();
        try(var c=connection()) {
            try {
                String schema="attitude_assessment_jpa_"+UUID.randomUUID().toString().replace("-","");
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                for(var table:List.of("staff","role","org_chart","employee_level","annual_kpi_review_period",
                        "review_period_employee_level_configuration","review_period_role_configuration","review_period_participant",
                        "attitude_configuration","attitude_criterion","attitude_rating_definition","attitude_role_format_mapping")) {
                    sql(c,"CREATE TABLE "+schema+"."+table+" (LIKE public."+table+" INCLUDING ALL)");
                    sql(c,"INSERT INTO "+schema+"."+table+" SELECT * FROM public."+table);
                }
                sql(c,"ALTER TABLE annual_kpi_review_period DROP CONSTRAINT IF EXISTS uq_period_attitude_configuration");
                sql(c,"ALTER TABLE attitude_criterion DROP CONSTRAINT IF EXISTS uq_attitude_criterion_configuration");
                for(var table:List.of("attitude_configuration","attitude_criterion","attitude_role_format_mapping"))
                    sql(c,"ALTER TABLE "+table+" ALTER COLUMN id RESTART WITH "+scalar(c,"SELECT coalesce(max(id),0)+1 FROM "+table));
                String before=text(c,"SELECT md5(string_agg(to_jsonb(p)::text,',' ORDER BY id)) FROM annual_kpi_review_period p");
                sql(c,migration());
                assertEquals(before,text(c,"SELECT md5(string_agg(to_jsonb(p)::text,',' ORDER BY id)) FROM annual_kpi_review_period p"));
                var config=new Configuration();config.setProperty("hibernate.connection.url",env.get("DB_URL"));
                config.setProperty("hibernate.connection.username",env.get("DB_USER"));config.setProperty("hibernate.connection.password",env.get("DB_PASS"));
                config.setProperty("hibernate.hbm2ddl.auto","none");config.setProperty("hibernate.default_schema",schema);
                config.setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
                var scanner=new ClassPathScanningCandidateComponentProvider(false);scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
                for(var candidate:scanner.findCandidateComponents("com.tbm.careerpathlearning.model"))config.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
                try(var sf=config.buildSessionFactory();var session=sf.withOptions().connection(c).openSession()) {
                    session.beginTransaction();var factory=new JpaRepositoryFactory(session);
                    var periods=factory.getRepository(AnnualKpiReviewPeriodRepository.class);var staff=factory.getRepository(StaffRepository.class);
                    var participants=factory.getRepository(ReviewPeriodParticipantRepository.class);
                    var assessments=factory.getRepository(AttitudeAssessmentRepository.class);
                    var p=session.createQuery("select p from ReviewPeriodParticipant p where p.role is not null and p.staff.isDeleted=false "
                            +"and p.staff.accountStatus=com.tbm.careerpathlearning.enums.StaffAccountStatus.ACTIVE",ReviewPeriodParticipant.class).setMaxResults(1).getSingleResult();
                    var employee=p.getStaff();var actor=employee.getId();
                    var superior=session.createQuery("select s from Staff s where s.id<>:actor and s.isDeleted=false "
                            +"and s.accountStatus=com.tbm.careerpathlearning.enums.StaffAccountStatus.ACTIVE",Staff.class)
                            .setParameter("actor",actor).setMaxResults(1).getSingleResult();
                    employee.setManager(superior);staff.saveAndFlush(employee);
                    var clock=Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"),ZoneId.of("Asia/Kuala_Lumpur"));
                    var setup=new AttitudeConfigurationServiceImpl(factory.getRepository(AttitudeConfigurationRepository.class),periods,
                            factory.getRepository(ReviewPeriodRoleConfigurationRepository.class),factory.getRepository(RoleRepository.class),staff,
                            Mappers.getMapper(AttitudeConfigurationMapper.class),clock);
                    var request=AttitudeConfigurationServiceTest.complete();request.getRoleMappings().get(0).setRoleId(p.getRole().getId());
                    var draft=setup.create(request,actor);setup.publish(draft.getId(),actor);
                    var period=p.getReviewPeriod();period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
                    period.setAttitudeConfiguration(factory.getRepository(AttitudeConfigurationRepository.class).findById(draft.getId()).orElseThrow());
                    periods.saveAndFlush(period);
                    var email=mock(EmailService.class);
                    var service=new AttitudeAssessmentServiceImpl(assessments,participants,periods,staff,Mappers.getMapper(AttitudeAssessmentMapper.class),
                            Mappers.getMapper(AttitudeConfigurationMapper.class),Mappers.getMapper(KpiPlanMapper.class),email,clock);
                    var view=service.mine(period.getId(),actor);assertNull(view.getId());assertTrue(view.isAvailable());
                    var body=new AttitudeAssessmentRequest();body.setReviewPeriodId(period.getId());var saved=service.create(body,actor);
                    session.clear();var loaded=service.get(saved.getId(),actor);assertEquals(1,loaded.getItems().size());assertNull(loaded.getItems().get(0).getSelfPoint());
                    var answer=new AttitudeAssessmentRequest.Answer();answer.setCriterionId(loaded.getItems().get(0).getCriterionId());answer.setSelfPoint(5);answer.setSelfComment("Reflection");
                    body.setItems(List.of(answer));service.update(saved.getId(),body,actor);session.clear();
                    assertEquals("Reflection",service.get(saved.getId(),actor).getItems().get(0).getSelfComment());
                    var newer=setup.copy(draft.getId(),actor);setup.publish(newer.getId(),actor);session.clear();
                    assertEquals(draft.getId(),service.mine(period.getId(),actor).getConfigurationId());
                    // A database rollback restores Draft state and answers; no new live assessment exists.
                    var rollback=c.setSavepoint();service.submit(saved.getId(),actor);session.clear();
                    assertEquals(AttitudeAssessmentStatus.PENDING_REVIEW,service.get(saved.getId(),actor).getStatus());
                    assertNull(assessments.findById(saved.getId()).orElseThrow().getAttitudeScore());c.rollback(rollback);session.clear();
                    assertEquals(AttitudeAssessmentStatus.DRAFT,service.get(saved.getId(),actor).getStatus());
                    var submitted=service.submit(saved.getId(),actor);session.clear();
                    assertEquals(superior.getId(),submitted.getSubmittedToSuperiorId());assertFalse(submitted.isCanSaveDraft());
                    assertEquals(5,service.get(saved.getId(),actor).getItems().get(0).getSelfPoint());
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(superior.getId(),null,
                            List.of(new SimpleGrantedAuthority("CAN_REVIEW_ATTITUDE_EVALUATION"))));
                    var queue=service.reviews(period.getId(),AttitudeAssessmentStatus.PENDING_REVIEW,superior.getId());
                    assertEquals(1,queue.size());assertFalse(queue.get(0).isSuperiorDraftSaved());
                    var superiorRequest=new AttitudeSuperiorAssessmentRequest();
                    service.saveSuperiorDraft(saved.getId(),superiorRequest,superior.getId());session.clear();
                    assertTrue(service.get(saved.getId(),superior.getId()).isSuperiorDraftSaved());
                    var superiorAnswer=new AttitudeSuperiorAssessmentRequest.Answer();superiorAnswer.setItemId(loaded.getItems().get(0).getId());
                    superiorAnswer.setSuperiorPoint(3);superiorAnswer.setSuperiorComment("Observed behaviour");superiorRequest.setItems(List.of(superiorAnswer));
                    service.saveSuperiorDraft(saved.getId(),superiorRequest,superior.getId());session.clear();
                    assertNull(service.get(saved.getId(),actor).getItems().get(0).getSuperiorPoint());
                    var reviewRollback=c.setSavepoint();var reviewed=service.completeReview(saved.getId(),superior.getId());session.clear();
                    assertEquals(new BigDecimal("60.0000"),reviewed.getAttitudeScore());
                    assertEquals(3,service.get(saved.getId(),actor).getItems().get(0).getSuperiorPoint());
                    assertEquals(5,service.get(saved.getId(),actor).getItems().get(0).getSelfPoint());
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.completeReview(saved.getId(),superior.getId()));
                    c.rollback(reviewRollback);session.clear();
                    assertEquals(AttitudeAssessmentStatus.PENDING_REVIEW,service.get(saved.getId(),superior.getId()).getStatus());
                    assertNull(service.get(saved.getId(),superior.getId()).getAttitudeScore());
                    assertEquals(AttitudeAssessmentStatus.REVIEWED,service.completeReview(saved.getId(),superior.getId()).getStatus());
                    session.getTransaction().rollback();
                }
            } finally {SecurityContextHolder.clearContext();c.rollback();}
        }
    }
}
