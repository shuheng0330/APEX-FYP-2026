package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.KpiPlanValidator;
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
import java.sql.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="APEX_PHASE2_POSTGRES_TEST",matches="true")
class KpiPlanPostgresTest {
    @Test void forwardMigrationAndJpaPlanRoundTripAreTransactional() throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        var schema="apex_kpi_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                for(var table:List.of("org_chart","role","staff","authority","employee_level","annual_kpi_review_period",
                        "review_period_employee_level_configuration","review_period_role_configuration","review_checkpoint","review_period_participant"))
                    sql(c,"CREATE TABLE "+schema+"."+table+" (LIKE public."+table+" INCLUDING ALL)");
                sql(c,"ALTER TABLE review_period_participant DROP CONSTRAINT IF EXISTS uq_participant_id_period");
                sql(c,new ClassPathResource("db/migration/annual-kpi/V31__kpi_plan_foundation.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                sql(c,"ALTER TABLE annual_kpi_review_period DROP COLUMN IF EXISTS participants_snapshotted_at");
                sql(c,new ClassPathResource("db/migration/annual-kpi/V32__review_participant_snapshot_and_company_publication.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                // Clone only inherited actors inside the rollback-only test schema; no live Role grants.
                sql(c,"INSERT INTO org_chart SELECT * FROM public.org_chart");
                sql(c,"INSERT INTO role SELECT * FROM public.role");sql(c,"INSERT INTO staff SELECT * FROM public.staff");
                sql(c,"INSERT INTO employee_level SELECT * FROM public.employee_level");
                for(var table:List.of("org_chart","role"))
                    sql(c,"ALTER TABLE "+schema+"."+table+" ALTER COLUMN id RESTART WITH "+(scalar(c,"SELECT coalesce(max(id),0) FROM "+table)+1));
                var config=new Configuration();config.setProperty("hibernate.connection.url",url);
                config.setProperty("hibernate.connection.username",env.get("DB_USER"));config.setProperty("hibernate.connection.password",env.get("DB_PASS"));
                config.setProperty("hibernate.hbm2ddl.auto","none");config.setProperty("hibernate.default_schema",schema);
                config.setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
                var scanner=new ClassPathScanningCandidateComponentProvider(false);scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
                for(var candidate:scanner.findCandidateComponents("com.tbm.careerpathlearning.model")) config.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
                try(var sf=config.buildSessionFactory();var session=sf.withOptions().connection(c).openSession()) {
                    session.beginTransaction();
                    var factory=new JpaRepositoryFactory(session);var periods=factory.getRepository(AnnualKpiReviewPeriodRepository.class);
                    var plans=factory.getRepository(KpiPlanRepository.class);
                    var period=new AnnualKpiReviewPeriod();period.setName("KPI isolated draft");periods.saveAndFlush(period);
                    UUID actor=session.createQuery("select s.id from Staff s",UUID.class).setMaxResults(1).getSingleResult();
                    var service=new KpiPlanServiceImpl(plans,periods,Mappers.getMapper(KpiPlanMapper.class),new KpiPlanValidator(),Clock.systemUTC(),
                        new com.tbm.careerpathlearning.service.KpiAssignmentService(factory.getRepository(ReviewPeriodParticipantRepository.class),factory.getRepository(EmployeeKpiAssignmentRepository.class),Clock.systemUTC()));
                    var request=new KpiPlanRequest();request.setReviewPeriodId(period.getId());request.setItems(List.of(com.tbm.careerpathlearning.service.KpiPlanValidatorTest.item("Sales","100")));
                    var result=service.createCompany(request,actor);session.clear();
                    var loaded=service.companyPlan(result.getId());assertEquals(5,loaded.getItems().get(0).getScoringDefinitions().size());
                    request.setItems(loaded.getItems());request.getItems().get(0).setName("Revised Draft");
                    service.updateCompany(result.getId(),request,actor);session.clear();assertEquals("Revised Draft",service.companyPlan(result.getId()).getItems().get(0).getName());
                    request.setItems(List.of());service.updateCompany(result.getId(),request,actor);session.clear();assertTrue(service.companyPlan(result.getId()).getItems().isEmpty());
                    request.setItems(List.of(com.tbm.careerpathlearning.service.KpiPlanValidatorTest.item("Sales","60"),
                        com.tbm.careerpathlearning.service.KpiPlanValidatorTest.item("Customers","40")));
                    service.updateCompany(result.getId(),request,actor);
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.publishCompany(result.getId(),actor));
                    var savedPeriod=periods.findById(period.getId()).orElseThrow();
                    Mappers.getMapper(com.tbm.careerpathlearning.mapper.AnnualKpiReviewPeriodMapper.class)
                        .updateConfiguration(AnnualKpiReviewPeriodServiceImplTest.validRequest(),savedPeriod);
                    savedPeriod.setStatus(AnnualKpiReviewPeriodStatus.OPEN);savedPeriod.setParticipantsSnapshottedAt(OffsetDateTime.now());periods.saveAndFlush(savedPeriod);
                    var department=new OrgChart();department.setName("Company cascade test department");department.setType(OrgChartType.D);
                    department.setCreatedAt(OffsetDateTime.now());department.setUpdatedAt(department.getCreatedAt());session.persist(department);
                    var role=new Role();role.setName("Company cascade test role");role.setOrgChart(department);
                    role.setCreatedAt(OffsetDateTime.now());role.setUpdatedAt(role.getCreatedAt());role.setEmployeeLevel(session.find(EmployeeLevel.class,4L));session.persist(role);
                    var weights=new ReviewPeriodEmployeeLevelConfiguration();weights.setReviewPeriod(savedPeriod);weights.setEmployeeLevel(role.getEmployeeLevel());
                    weights.setCompanyKpiWeight(new java.math.BigDecimal("15"));weights.setDepartmentKpiWeight(new java.math.BigDecimal("25"));
                    weights.setIndividualKpiWeight(new java.math.BigDecimal("60"));session.persist(weights);session.flush();
                    var roleConfiguration=new ReviewPeriodRoleConfiguration();roleConfiguration.setReviewPeriod(savedPeriod);roleConfiguration.setRole(role);
                    roleConfiguration.setEmployeeLevelConfiguration(weights);roleConfiguration.setEmployeeLevelConfigurationId(weights.getId());
                    roleConfiguration.setReviewFrequency(ReviewFrequency.MONTHLY);session.persist(roleConfiguration);
                    var participants=factory.getRepository(ReviewPeriodParticipantRepository.class);
                    for(int i=0;i<2;i++) {
                        var employee=new Staff();employee.setId(UUID.randomUUID());employee.setName("Company cascade employee "+i);
                        employee.setEmail(employee.getId()+"@example.test");employee.setRole(role);employee.setAccountStatus(StaffAccountStatus.ACTIVE);
                        employee.setCreatedAt(OffsetDateTime.now());employee.setUpdatedAt(employee.getCreatedAt());session.persist(employee);
                        participants.saveAndFlush(new com.tbm.careerpathlearning.service.ReviewPeriodParticipantFactory().snapshot(savedPeriod,employee,roleConfiguration,department));
                    }
                    var rosterIds=participants.findAllByReviewPeriodId(savedPeriod.getId()).stream().map(ReviewPeriodParticipant::getId).toList();
                    var published=service.publishCompany(result.getId(),actor);assertEquals(KpiPlanStatus.PUBLISHED,published.getStatus());
                    assertEquals(4L,scalar(c,"SELECT count(*) FROM employee_kpi_assignment"));
                    assertEquals(rosterIds,participants.findAllByReviewPeriodId(savedPeriod.getId()).stream().map(ReviewPeriodParticipant::getId).toList());
                    assertEquals(0,new java.math.BigDecimal("100").compareTo(published.getTotalWeightage()));
                    reject(c,"23505","INSERT INTO employee_kpi_assignment(kpi_id,participant_id,review_period_id,assigned_at) "
                        +"SELECT kpi_id,participant_id,review_period_id,assigned_at FROM employee_kpi_assignment");
                    var other=new AnnualKpiReviewPeriod();other.setName("Cross-period assignment guard");periods.saveAndFlush(other);
                    reject(c,"23503","UPDATE employee_kpi_assignment SET review_period_id="+other.getId());
                    reject(c,"23514","UPDATE kpi_plan SET published_at=NULL WHERE id="+published.getId());
                    assertNotNull(published.getPublishedAt());assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.publishCompany(result.getId(),actor));
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.updateCompany(result.getId(),request,actor));
                    session.getTransaction().rollback();
                }
            } finally {c.rollback();}
        }
    }
    private void sql(Connection c,String sql)throws SQLException {try(var s=c.createStatement()){s.execute(sql);}}
    private long scalar(Connection c,String sql)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private void reject(Connection c,String state,String sql)throws SQLException {
        var savepoint=c.setSavepoint();
        try {assertEquals(state,assertThrows(SQLException.class,()->sql(c,sql)).getSQLState());}
        finally {c.rollback(savepoint);c.releaseSavepoint(savepoint);}
    }
}
