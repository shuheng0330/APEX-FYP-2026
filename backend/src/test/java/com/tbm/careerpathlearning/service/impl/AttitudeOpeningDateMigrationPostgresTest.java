package com.tbm.careerpathlearning.service.impl;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.tbm.careerpathlearning.service.impl.AttitudeConfigurationPostgresTest.*;

@EnabledIfEnvironmentVariable(named="APEX_PHASE3_POSTGRES_TEST",matches="true")
class AttitudeOpeningDateMigrationPostgresTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void migrationPreservesPublishedAvailabilityDraftsAndAllExistingValues(boolean ormColumn) throws Exception {
        isolated(c->{
            fixture(c);
            if(ormColumn) sql(c,"ALTER TABLE annual_kpi_review_period ADD COLUMN attitude_self_assessment_start_date DATE");
            String before=digest(c,"annual_kpi_review_period");
            String answers=digest(c,"attitude_assessment");
            sql(c,migration());
            assertEquals(before,digest(c,"annual_kpi_review_period"));
            assertEquals(answers,digest(c,"attitude_assessment"));
            assertEquals(3,scalar(c,"SELECT count(*) FROM annual_kpi_review_period WHERE status<>'DRAFT' AND attitude_self_assessment_start_date=start_date"));
            assertEquals(2,scalar(c,"SELECT count(*) FROM annual_kpi_review_period WHERE status='DRAFT' AND attitude_self_assessment_start_date IS NULL"));
            reject(c,"23514","UPDATE annual_kpi_review_period SET attitude_self_assessment_start_date=NULL WHERE id=4");
            reject(c,"23514","UPDATE annual_kpi_review_period SET attitude_self_assessment_start_date=start_date-1 WHERE id=2");
            reject(c,"23514","UPDATE annual_kpi_review_period SET attitude_self_assessment_start_date=attitude_self_assessment_deadline+1 WHERE id=2");
            sql(c,"UPDATE annual_kpi_review_period SET attitude_self_assessment_start_date=attitude_self_assessment_deadline WHERE id=2");
            sql(c,"UPDATE annual_kpi_review_period SET end_date='2027-09-30',attitude_self_assessment_start_date='2027-10-01' WHERE id=3");
            assertEquals(1,scalar(c,"SELECT count(*) FROM annual_kpi_review_period WHERE id=3 AND attitude_self_assessment_start_date>end_date"));
        });
    }

    @ParameterizedTest @ValueSource(strings={"TEXT","DATE NOT NULL DEFAULT DATE '2027-01-01'","DATE DEFAULT DATE '2027-01-01'"})
    void incompatibleOrmColumnIsRejected(String definition) throws Exception {
        isolated(c->{
            fixture(c);sql(c,"ALTER TABLE annual_kpi_review_period ADD COLUMN attitude_self_assessment_start_date "+definition);
            var savepoint=c.setSavepoint();
            assertEquals("23514",assertThrows(SQLException.class,()->sql(c,migration())).getSQLState());
            c.rollback(savepoint);
        });
    }

    @Test void conflictingUntrackedPublishedDatesAreNotOverwritten() throws Exception {
        isolated(c->{
            fixture(c);sql(c,"ALTER TABLE annual_kpi_review_period ADD COLUMN attitude_self_assessment_start_date DATE");
            sql(c,"UPDATE annual_kpi_review_period SET attitude_self_assessment_start_date='2027-10-01' WHERE id=3");
            var savepoint=c.setSavepoint();
            assertEquals("23514",assertThrows(SQLException.class,()->sql(c,migration())).getSQLState());
            c.rollback(savepoint);
            assertEquals("2027-10-01",text(c,"SELECT attitude_self_assessment_start_date::text FROM annual_kpi_review_period WHERE id=3"));
        });
    }

    private void fixture(Connection c) throws SQLException {
        sql(c,"CREATE TABLE annual_kpi_review_period(id BIGINT PRIMARY KEY,status VARCHAR(20),start_date DATE,end_date DATE,"
                +"attitude_self_assessment_deadline DATE,superior_attitude_evaluation_deadline DATE,attitude_configuration_id BIGINT,"
                +"created_at TIMESTAMPTZ DEFAULT '2025-01-01T00:00:00Z',updated_at TIMESTAMPTZ DEFAULT '2025-01-02T00:00:00Z')");
        sql(c,"INSERT INTO annual_kpi_review_period(id,status,start_date,end_date,attitude_self_assessment_deadline,superior_attitude_evaluation_deadline,attitude_configuration_id) VALUES"
                +"(1,'DRAFT',NULL,NULL,NULL,NULL,NULL),"
                +"(2,'DRAFT','2027-01-01','2027-12-31','2027-12-05','2027-12-15',NULL),"
                +"(3,'UPCOMING','2027-01-01','2027-12-31','2027-12-05','2027-12-15',10),"
                +"(4,'OPEN','2026-01-01','2026-12-31','2026-12-05','2026-12-15',10),"
                +"(5,'CLOSED','2025-01-01','2025-12-31','2025-12-05','2025-12-15',11)");
        sql(c,"CREATE TABLE attitude_assessment(id BIGINT PRIMARY KEY,period_id BIGINT,status TEXT,answers JSONB,score NUMERIC,updated_at TIMESTAMPTZ)");
        sql(c,"INSERT INTO attitude_assessment VALUES (1,4,'DRAFT','{\"selfPoint\":3}',NULL,'2026-10-01'),"
                +"(2,4,'PENDING_REVIEW','{\"selfPoint\":4}',NULL,'2026-10-02'),"
                +"(3,5,'REVIEWED','{\"selfPoint\":5,\"superiorPoint\":4}',80,'2025-12-15')");
    }

    private String migration() throws Exception {
        return new ClassPathResource("db/migration/annual-kpi/V47__attitude_self_assessment_opening_date.sql").getContentAsString(StandardCharsets.UTF_8);
    }
    private String digest(Connection c,String table) throws SQLException {
        return text(c,"SELECT jsonb_agg(to_jsonb(t)-'attitude_self_assessment_start_date' ORDER BY id)::text FROM "+table+" t");
    }
    private void isolated(DatabaseWork work) throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                var schema="attitude_opening_test_"+UUID.randomUUID().toString().replace("-","");
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                work.run(c);
            } finally {c.rollback();}
        }
    }
    @FunctionalInterface private interface DatabaseWork {void run(Connection c) throws Exception;}
}
