package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.mapper.AttitudeConfigurationMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.AttitudeConfigurationBinding;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="APEX_PHASE3_POSTGRES_TEST",matches="true")
class AttitudeConfigurationPostgresTest {
    @Test void forwardMigrationAndJpaEditingPreservePeriodsAndPublishedEditions() throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                var schema="attitude_test_"+UUID.randomUUID().toString().replace("-","");
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                for(var table:List.of("staff","role","org_chart","employee_level","authority","annual_kpi_review_period",
                        "review_period_role_configuration","review_period_employee_level_configuration","review_period_participant")) {
                    sql(c,"CREATE TABLE "+schema+"."+table+" (LIKE public."+table+" INCLUDING ALL)");
                    sql(c,"INSERT INTO "+schema+"."+table+" SELECT * FROM public."+table);
                }
                sql(c,"DELETE FROM authority WHERE name='CAN_MANAGE_ATTITUDE_CONFIGURATION'");
                sql(c,"ALTER TABLE annual_kpi_review_period DROP COLUMN IF EXISTS attitude_configuration_id");
                sql(c,"CREATE TABLE role_authority(role_id bigint,authority_id bigint,created_at timestamptz,updated_at timestamptz,PRIMARY KEY(role_id,authority_id))");
                for(var table:List.of("authority","annual_kpi_review_period"))
                    sql(c,"ALTER TABLE "+table+" ALTER COLUMN id RESTART WITH "+(scalar(c,"SELECT coalesce(max(id),0)+1 FROM "+table)));
                long count=scalar(c,"SELECT count(*) FROM annual_kpi_review_period");
                String before=text(c,"SELECT md5(coalesce(string_agg((to_jsonb(p)-'attitude_configuration_id')::text,',' ORDER BY id),'')) FROM annual_kpi_review_period p");
                sql(c,new ClassPathResource("db/migration/annual-kpi/V40__attitude_evaluation_configuration.sql").getContentAsString(StandardCharsets.UTF_8));
                assertEquals(count,scalar(c,"SELECT count(*) FROM annual_kpi_review_period"));
                assertEquals(before,text(c,"SELECT md5(coalesce(string_agg((to_jsonb(p)-'attitude_configuration_id')::text,',' ORDER BY id),'')) FROM annual_kpi_review_period p"));
                assertEquals(0,scalar(c,"SELECT count(*) FROM annual_kpi_review_period WHERE attitude_configuration_id IS NOT NULL"));
                assertEquals(1,scalar(c,"SELECT count(*) FROM role_authority ra JOIN authority a ON a.id=ra.authority_id WHERE a.name='CAN_MANAGE_ATTITUDE_CONFIGURATION'"));
                var config=new Configuration();config.setProperty("hibernate.connection.url",url);config.setProperty("hibernate.connection.username",env.get("DB_USER"));
                config.setProperty("hibernate.connection.password",env.get("DB_PASS"));config.setProperty("hibernate.hbm2ddl.auto","none");
                config.setProperty("hibernate.default_schema",schema);
                config.setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
                var scanner=new ClassPathScanningCandidateComponentProvider(false);scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
                for(var candidate:scanner.findCandidateComponents("com.tbm.careerpathlearning.model"))config.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
                try(var sf=config.buildSessionFactory();var session=sf.withOptions().connection(c).openSession()) {
                    session.beginTransaction();var factory=new JpaRepositoryFactory(session);
                    var configurations=factory.getRepository(AttitudeConfigurationRepository.class);var periods=factory.getRepository(AnnualKpiReviewPeriodRepository.class);
                    var clock=Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"),ZoneId.of("Asia/Kuala_Lumpur"));
                    var service=new AttitudeConfigurationServiceImpl(configurations,periods,factory.getRepository(ReviewPeriodRoleConfigurationRepository.class),
                            factory.getRepository(RoleRepository.class),factory.getRepository(StaffRepository.class),Mappers.getMapper(AttitudeConfigurationMapper.class),clock);
                    var actor=session.createQuery("select s.id from Staff s where s.accountStatus=:status and s.isDeleted=false",UUID.class)
                            .setParameter("status",StaffAccountStatus.ACTIVE).setMaxResults(1).getSingleResult();
                    var role=session.createQuery("select r.id from Role r where r.performanceReviewEligible=true and r.isDeleted=false",Long.class).setMaxResults(1).getSingleResult();
                    var request=AttitudeConfigurationServiceTest.complete();request.getRoleMappings().get(0).setRoleId(role);
                    var draft=service.create(request,actor);session.clear();var loaded=service.get(draft.getId(),actor);
                    assertEquals(5,loaded.getRatingDefinitions().size());assertEquals(1,loaded.getCriteria().size());
                    request.getCriteria().get(0).setId(loaded.getCriteria().get(0).getId());request.getCriteria().get(0).setName("Updated Draft criterion");
                    request.getRatingDefinitions().get(0).setLabel("Updated rating");
                    request.getRoleMappings().get(0).setEvaluationFormat(AttitudeEvaluationFormat.MANAGER);
                    service.update(draft.getId(),request,actor);session.clear();
                    var updated=service.get(draft.getId(),actor);assertEquals("Updated rating",updated.getRatingDefinitions().get(4).getLabel());
                    assertEquals(AttitudeEvaluationFormat.MANAGER,updated.getRoleMappings().get(0).getEvaluationFormat());
                    service.publish(draft.getId(),actor);session.clear();
                    assertEquals(AttitudeConfigurationStatus.PUBLISHED,service.current(actor).getStatus());
                    var period=new AnnualKpiReviewPeriod();period.setName("Isolated binding test");
                    period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);period.setStartDate(LocalDate.of(2025,1,1));period.setEndDate(LocalDate.of(2025,12,31));
                    period.setAnnualKpiConsolidationMethod(AnnualKpiConsolidationMethod.FINAL_CHECKPOINT);
                    period.setSelfAssessmentDaysAfterCheckpoint(5);period.setSuperiorAssessmentDaysAfterSelfDeadline(5);
                    period.setKpiSetupDeadline(period.getStartDate());
                    period.setOpenedAt(OffsetDateTime.now(clock));periods.saveAndFlush(period);
                    var bound=service.bindInitially(period.getId(),draft.getId(),actor);assertEquals(draft.getId(),bound.getConfiguration().getId());
                    var copy=service.copy(draft.getId(),actor);session.clear();
                    var copied=service.get(copy.getId(),actor);assertNotEquals(loaded.getCriteria().get(0).getId(),copied.getCriteria().get(0).getId());
                    var revision=AttitudeConfigurationServiceTest.complete();revision.getRoleMappings().get(0).setRoleId(role);
                    revision.getCriteria().get(0).setId(copied.getCriteria().get(0).getId());revision.getCriteria().get(0).setName("Next edition");
                    service.update(copy.getId(),revision,actor);service.publish(copy.getId(),actor);session.clear();
                    assertEquals(copy.getId(),service.current(actor).getId());assertEquals(draft.getId(),service.period(period.getId(),actor).getConfiguration().getId());
                    assertEquals("Updated Draft criterion",service.get(draft.getId(),actor).getCriteria().get(0).getName());
                    var nextPeriod=new AnnualKpiReviewPeriod();nextPeriod.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
                    new AttitudeConfigurationBinding(configurations).bindOnOpening(nextPeriod);assertEquals(copy.getId(),nextPeriod.getAttitudeConfiguration().getId());
                    // Replacing composite-key ratings must remove absent points before a later re-add.
                    var editable=service.copy(copy.getId(),actor);service.update(editable.getId(),new AttitudeConfigurationRequest(),actor);session.clear();
                    assertTrue(service.get(editable.getId(),actor).getRatingDefinitions().isEmpty());
                    service.update(editable.getId(),revisionWithoutIds(role),actor);session.clear();assertEquals(5,service.get(editable.getId(),actor).getRatingDefinitions().size());
                    reject(c,"23514","UPDATE attitude_rating_definition SET point=6 WHERE configuration_id="+editable.getId());
                    reject(c,"23505","INSERT INTO attitude_role_format_mapping(configuration_id,role_id,evaluation_format) VALUES("+editable.getId()+","+role+",'SALES')");
                    reject(c,"23514","UPDATE attitude_criterion SET evaluation_format='SALES' WHERE configuration_id="+editable.getId());
                    reject(c,"23503","DELETE FROM attitude_configuration WHERE id="+draft.getId());
                    reject(c,"23514","UPDATE attitude_configuration SET published_at=NULL WHERE id="+draft.getId());
                    session.getTransaction().rollback();
                }
            } finally {c.rollback();}
        }
    }
    @Test void emptyPeriodFixtureRejectsInvalidMetadataAndAmbiguousAdministratorGrant() throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                var schema="attitude_empty_"+UUID.randomUUID().toString().replace("-","");sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                sql(c,"CREATE TABLE staff(id uuid PRIMARY KEY)");sql(c,"INSERT INTO staff VALUES('00000000-0000-0000-0000-000000000001')");
                sql(c,"CREATE TABLE role(id bigint PRIMARY KEY,name text,is_deleted boolean)");sql(c,"INSERT INTO role VALUES(1,'superadmin',false),(2,'Employee',false)");
                sql(c,"CREATE TABLE authority(id bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,name varchar(100) UNIQUE,description_key text,label_key text)");
                sql(c,"CREATE TABLE role_authority(role_id bigint,authority_id bigint,created_at timestamptz,updated_at timestamptz,PRIMARY KEY(role_id,authority_id))");
                sql(c,"CREATE TABLE annual_kpi_review_period(id bigint PRIMARY KEY)");
                var migration=new ClassPathResource("db/migration/annual-kpi/V40__attitude_evaluation_configuration.sql").getContentAsString(StandardCharsets.UTF_8);
                var before=c.setSavepoint();sql(c,"INSERT INTO role VALUES(3,'superadmin',false)");
                assertThrows(SQLException.class,()->sql(c,migration));c.rollback(before);
                assertEquals(0,scalar(c,"SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name='attitude_configuration'"));
                sql(c,migration);assertEquals(0,scalar(c,"SELECT count(*) FROM annual_kpi_review_period"));
                sql(c,"INSERT INTO attitude_configuration(id,created_at,updated_at,created_by,updated_by) VALUES(1,now(),now(),'00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001')");
                reject(c,"23514","UPDATE attitude_configuration SET status='PUBLISHED',published_at=now(),published_by=created_by");
                reject(c,"23514","INSERT INTO attitude_rating_definition(configuration_id,point) VALUES(1,0)");
                reject(c,"23503","INSERT INTO attitude_rating_definition(configuration_id,point) VALUES(99,1)");
                reject(c,"23514","INSERT INTO attitude_criterion(configuration_id,criterion_type,display_order) VALUES(1,'FORMAT_SPECIFIC',0)");
                sql(c,"INSERT INTO attitude_rating_definition(configuration_id,point) VALUES(1,1)");
                reject(c,"23505","INSERT INTO attitude_rating_definition(configuration_id,point) VALUES(1,1)");
                reject(c,"23503","INSERT INTO attitude_role_format_mapping(configuration_id,role_id,evaluation_format) VALUES(1,99,'SALES')");
            } finally {c.rollback();}
        }
    }
    static AttitudeConfigurationRequest revisionWithoutIds(Long role) {var request=AttitudeConfigurationServiceTest.complete();request.getRoleMappings().get(0).setRoleId(role);return request;}
    static void sql(Connection c,String sql) throws SQLException {try(var s=c.createStatement()){s.execute(sql);}}
    static long scalar(Connection c,String sql) throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    static String text(Connection c,String sql) throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    static void reject(Connection c,String state,String sql) throws SQLException {var savepoint=c.setSavepoint();var error=assertThrows(SQLException.class,()->sql(c,sql));assertEquals(state,error.getSQLState());c.rollback(savepoint);}
}
