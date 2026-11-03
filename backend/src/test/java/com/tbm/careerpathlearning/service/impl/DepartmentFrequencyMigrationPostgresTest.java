package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.repository.KpiCentralResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Query;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Requires an explicitly supplied disposable instance; never reads the application .env. */
@EnabledIfEnvironmentVariable(named="APEX_FREQUENCY_TEST_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:55449/postgres")
class DepartmentFrequencyMigrationPostgresTest {
    @Test void migrationPreservesExistingValuesAndSeparatesQuarterlyCompanyReference() throws Exception {
        isolated(c -> {
            String before=text(c,"SELECT to_jsonb(t)::text FROM kpi_checkpoint_result t WHERE id=1");
            migrate(c);
            assertEquals(before,text(c,"SELECT (to_jsonb(t)-'company_checkpoint_id')::text FROM kpi_checkpoint_result t WHERE id=1"));
            assertEquals("ROLE_BASED",text(c,"SELECT frequency_policy FROM annual_kpi_review_period WHERE id=1"));
            assertEquals("1",text(c,"SELECT company_checkpoint_id::text FROM kpi_checkpoint_result WHERE id=1"));
            newPolicy(c);
            sql(c,"INSERT INTO kpi_checkpoint_result VALUES(2,2,2,23,'QUARTERLY',11,12,14,0,NULL,'[]',0,21)");
            assertEquals("21",text(c,"SELECT company_checkpoint_id::text FROM kpi_checkpoint_result WHERE id=2"));
            reject(c,"UPDATE kpi_checkpoint_result SET company_checkpoint_id=22 WHERE id=2");
            reject(c,"INSERT INTO kpi_checkpoint_result VALUES(3,2,2,23,'QUARTERLY',11,12,14,80,now(),'[]',0,22)");
            reject(c,"INSERT INTO kpi_checkpoint_result VALUES(3,2,2,23,'QUARTERLY',11,12,14,80,now(),'[]',0,1)");
            reject(c,"UPDATE annual_kpi_review_period SET frequency_policy='DEPARTMENT_BASED' WHERE id=1");
        });
    }

    @Test void departmentSnapshotsAndPublishedSchedulesAreImmutable() throws Exception {
        isolated(c -> {
            migrate(c);newPolicy(c);
            sql(c,"UPDATE annual_kpi_review_period SET status='UPCOMING',participants_snapshotted_at=now() WHERE id=2");
            reject(c,"UPDATE review_period_department_configuration SET review_frequency='MONTHLY' WHERE review_period_id=2");
            reject(c,"DELETE FROM review_period_department_configuration WHERE review_period_id=2");
            reject(c,"UPDATE review_period_participant SET department_configuration_id=NULL WHERE id=2");
            reject(c,"UPDATE review_checkpoint SET end_date='2027-03-30' WHERE id=23");
            reject(c,"DELETE FROM review_checkpoint WHERE id=22");
            reject(c,"UPDATE annual_kpi_review_period SET end_date='2027-11-30' WHERE id=2");
            reject(c,"UPDATE annual_kpi_review_period SET status='DRAFT' WHERE id=2");
            sql(c,"UPDATE org_chart SET name='IT next year' WHERE id=7");
            assertEquals("IT",text(c,"SELECT department_name FROM review_period_department_configuration WHERE review_period_id=2"));
            sql(c,"UPDATE annual_kpi_review_period SET name='Renamed',status='OPEN' WHERE id=2");
        });
    }

    @Test void monthlyCompanyEligibilityIncludesQuarterlyEmployeesAndHonoursBoundVersions() throws Exception {
        isolated(c -> {
            migrate(c);newPolicy(c);
            var method=KpiCentralResultRepository.class.getMethod("eligibleParticipants",Long.class,Long.class);
            String query=method.getAnnotation(Query.class).value();
            assertEquals("1",text(c,query.replace(":versionId","11").replace(":checkpointId","21")));
            assertEquals("1",text(c,query.replace(":versionId","11").replace(":checkpointId","22")));
            assertEquals("0",text(c,query.replace(":versionId","11").replace(":checkpointId","23")));
            assertEquals("1",text(c,query.replace(":versionId","12").replace(":checkpointId","23")));
            assertEquals("0",text(c,query.replace(":versionId","12").replace(":checkpointId","21")));
            sql(c,"UPDATE kpi_plan SET current_version_id=99 WHERE id=1");
            assertEquals("1",text(c,query.replace(":versionId","11").replace(":checkpointId","21")));
            assertEquals("0",text(c,query.replace(":versionId","11").replace(":checkpointId","22")));
        });
    }

    private void newPolicy(Connection c) throws Exception {
        sql(c,"""
            INSERT INTO annual_kpi_review_period(id,status,assessment_model,frequency_policy,start_date,end_date) VALUES
                (2,'DRAFT','CENTRAL_RESULTS','DEPARTMENT_BASED','2027-01-01','2027-12-31');
            INSERT INTO review_period_department_configuration(review_period_id,department_id,department_name,review_frequency)
                VALUES(2,7,'IT','QUARTERLY');
            INSERT INTO review_period_participant VALUES(2,2,7,'QUARTERLY',1,1);
            INSERT INTO review_checkpoint VALUES(21,2,'MONTHLY','2027-03-01','2027-03-31'),
                (22,2,'MONTHLY','2027-02-01','2027-02-28'),(23,2,'QUARTERLY','2027-01-01','2027-03-31');
            INSERT INTO kpi_plan VALUES(1,11,7),(2,12,7);
            INSERT INTO kpi_plan_version VALUES(11,2,1,'COMPANY'),(12,2,2,'DEPARTMENT');
            INSERT INTO kpi_central_result VALUES(12,11,21),(14,12,23);
            INSERT INTO kpi VALUES(1,11),(2,12);
            INSERT INTO employee_kpi_assignment VALUES(2,1),(2,2);
            INSERT INTO kpi_assessment VALUES(1,2,23);
            INSERT INTO kpi_assessment_plan_version VALUES(1,11);
            """);
    }

    private void fixture(Connection c) throws Exception {
        sql(c,"""
            CREATE TABLE org_chart(id BIGINT PRIMARY KEY,name TEXT); INSERT INTO org_chart VALUES(7,'IT');
            CREATE TABLE annual_kpi_review_period(id BIGINT PRIMARY KEY,status TEXT,assessment_model TEXT,
                start_date DATE,end_date DATE,self_assessment_days_after_checkpoint INT,
                superior_assessment_days_after_self_deadline INT,participants_snapshotted_at TIMESTAMPTZ,name TEXT);
            INSERT INTO annual_kpi_review_period VALUES(1,'OPEN','CENTRAL_RESULTS','2026-01-01','2026-12-31',5,5,now(),'Old');
            CREATE TABLE review_period_participant(id BIGINT PRIMARY KEY,review_period_id BIGINT,department_id BIGINT,
                review_frequency TEXT,employee_level_configuration_id BIGINT);
            INSERT INTO review_period_participant VALUES(1,1,7,'QUARTERLY',1);
            CREATE TABLE review_checkpoint(id BIGINT PRIMARY KEY,review_period_id BIGINT,review_frequency TEXT,start_date DATE,end_date DATE);
            INSERT INTO review_checkpoint VALUES(1,1,'QUARTERLY','2026-01-01','2026-03-31');
            CREATE TABLE kpi_central_result(id BIGINT PRIMARY KEY,plan_version_id BIGINT,checkpoint_id BIGINT,UNIQUE(id,plan_version_id,checkpoint_id));
            INSERT INTO kpi_central_result VALUES(1,1,1);
            CREATE TABLE kpi_checkpoint_result(id BIGINT PRIMARY KEY,participant_id BIGINT,review_period_id BIGINT,checkpoint_id BIGINT,
                review_frequency TEXT,company_version_id BIGINT,company_result_id BIGINT,department_result_id BIGINT,
                score NUMERIC,calculated_at TIMESTAMPTZ,calculation_history JSONB,lock_version BIGINT,
                CONSTRAINT fk_checkpoint_company_result FOREIGN KEY(company_result_id,company_version_id,checkpoint_id)
                REFERENCES kpi_central_result(id,plan_version_id,checkpoint_id));
            INSERT INTO kpi_checkpoint_result VALUES(1,1,1,1,'QUARTERLY',1,1,NULL,80,now(),'[]',3);
            CREATE TABLE review_period_employee_level_configuration(id BIGINT PRIMARY KEY,company_kpi_weight NUMERIC,department_kpi_weight NUMERIC);
            INSERT INTO review_period_employee_level_configuration VALUES(1,50,30);
            CREATE TABLE kpi_plan(id BIGINT PRIMARY KEY,current_version_id BIGINT,department_id BIGINT);
            CREATE TABLE kpi_plan_version(id BIGINT PRIMARY KEY,review_period_id BIGINT,plan_id BIGINT,level TEXT);
            CREATE TABLE kpi(id BIGINT PRIMARY KEY,version_id BIGINT);
            CREATE TABLE employee_kpi_assignment(participant_id BIGINT,kpi_id BIGINT);
            CREATE TABLE kpi_assessment(id BIGINT PRIMARY KEY,participant_id BIGINT,checkpoint_id BIGINT);
            CREATE TABLE kpi_assessment_plan_version(assessment_id BIGINT,version_id BIGINT);
            """);
    }
    private void migrate(Connection c) throws Exception {
        sql(c,new ClassPathResource("db/migration/annual-kpi/V49__department_kpi_frequency.sql").getContentAsString(StandardCharsets.UTF_8));
    }
    private void isolated(Work work) throws Exception {
        try(var c=DriverManager.getConnection(System.getenv("APEX_FREQUENCY_TEST_URL"),"frequency_test","")) {
            c.setAutoCommit(false);
            try {
                String schema="frequency_"+UUID.randomUUID().toString().replace("-","");
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                fixture(c);work.run(c);
            } finally { c.rollback(); }
        }
    }
    private void reject(Connection c,String query) throws Exception {
        var savepoint=c.setSavepoint();assertThrows(SQLException.class,()->sql(c,query));c.rollback(savepoint);
    }
    private void sql(Connection c,String sql) throws SQLException {try(var s=c.createStatement()){s.execute(sql);}}
    private String text(Connection c,String sql) throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(sql)){assertTrue(r.next());return r.getString(1);}}
    interface Work {void run(Connection c) throws Exception;}
}
