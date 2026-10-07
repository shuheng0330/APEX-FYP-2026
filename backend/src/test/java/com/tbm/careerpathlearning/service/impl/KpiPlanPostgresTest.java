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
                // Clone only inherited actors inside the rollback-only test schema; no live Role grants.
                sql(c,"INSERT INTO org_chart SELECT * FROM public.org_chart");
                sql(c,"INSERT INTO role SELECT * FROM public.role");sql(c,"INSERT INTO staff SELECT * FROM public.staff");
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
                    var service=new KpiPlanServiceImpl(plans,periods,Mappers.getMapper(KpiPlanMapper.class),new KpiPlanValidator(),Clock.systemUTC());
                    var request=new KpiPlanRequest();request.setReviewPeriodId(period.getId());request.setItems(List.of(com.tbm.careerpathlearning.service.KpiPlanValidatorTest.item("Sales","100")));
                    var result=service.createCompany(request,actor);session.clear();
                    var loaded=service.companyPlan(result.getId());assertEquals(5,loaded.getItems().get(0).getScoringDefinitions().size());
                    request.setItems(loaded.getItems());request.getItems().get(0).setName("Revised Draft");
                    service.updateCompany(result.getId(),request,actor);session.clear();assertEquals("Revised Draft",service.companyPlan(result.getId()).getItems().get(0).getName());
                    request.setItems(List.of());service.updateCompany(result.getId(),request,actor);session.clear();assertTrue(service.companyPlan(result.getId()).getItems().isEmpty());
                    session.getTransaction().rollback();
                }
            } finally {c.rollback();}
        }
    }
    private void sql(Connection c,String sql)throws SQLException {try(var s=c.createStatement()){s.execute(sql);}}
}
