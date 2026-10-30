package com.tbm.careerpathlearning.service.impl;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="APEX_PHASE2_POSTGRES_TEST",matches="true")
class SubmissionRevisionMigrationPostgresTest {
    @ParameterizedTest @ValueSource(strings={"empty","populated","existing-column","wrong-type"})
    void migrationBackfillsReturnStateAndPreservesExistingContent(String scenario) throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        var schema="apex_revision_migration_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                sql(c,"CREATE TABLE kpi_plan(id BIGINT PRIMARY KEY,level TEXT,status TEXT)");
                sql(c,"CREATE TABLE appraisal_record(id BIGINT PRIMARY KEY,status TEXT,hr_return_reason TEXT)");
                if(!scenario.equals("empty")) {
                    sql(c,"INSERT INTO kpi_plan VALUES (1,'COMPANY','DRAFT'),(2,'DEPARTMENT','RETURNED'),"
                        +"(3,'INDIVIDUAL','RETURNED'),(4,'INDIVIDUAL','DRAFT'),(5,'DEPARTMENT','PENDING_APPROVAL')");
                    sql(c,"INSERT INTO appraisal_record VALUES (1,'RETURNED','Clarify'),(2,'DRAFT','Clarify'),"
                        +"(3,'DRAFT',NULL),(4,'APPROVED','Previous reason'),(5,'PENDING_REVIEW',NULL)");
                }
                if(scenario.equals("existing-column")) {
                    sql(c,"ALTER TABLE kpi_plan ADD COLUMN revision_required BOOLEAN");
                    sql(c,"ALTER TABLE appraisal_record ADD COLUMN revision_required BOOLEAN DEFAULT FALSE");
                }
                if(scenario.equals("wrong-type")) sql(c,"ALTER TABLE kpi_plan ADD COLUMN revision_required TEXT");
                var before=content(c);
                var migration=new ClassPathResource("db/migration/annual-kpi/V35__require_changes_after_return.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
                if(scenario.equals("wrong-type")) {
                    var savepoint=c.setSavepoint();
                    assertTrue(assertThrows(SQLException.class,()->sql(c,migration)).getMessage().contains("Incompatible untracked"));
                    c.rollback(savepoint);
                } else {
                    sql(c,migration);
                    long expected=scenario.equals("empty") ? 0 : 2;
                    assertEquals(expected,number(c,"SELECT count(*) FROM kpi_plan WHERE revision_required"));
                    assertEquals(expected,number(c,"SELECT count(*) FROM appraisal_record WHERE revision_required"));
                    sql(c,"INSERT INTO kpi_plan(id,level,status) VALUES (10,'COMPANY','DRAFT')");
                    assertEquals(0L,number(c,"SELECT count(*) FROM kpi_plan WHERE id=10 AND revision_required"));
                    reject(c,"23514","UPDATE kpi_plan SET revision_required=TRUE WHERE id=10");
                    reject(c,"23502","UPDATE kpi_plan SET revision_required=NULL WHERE id=10");
                    sql(c,"DELETE FROM kpi_plan WHERE id=10");
                    sql(c,"INSERT INTO appraisal_record(id,status) VALUES (10,'PENDING_REVIEW')");
                    reject(c,"23514","UPDATE appraisal_record SET revision_required=TRUE WHERE id=10");
                    reject(c,"23502","UPDATE appraisal_record SET revision_required=NULL WHERE id=10");
                    sql(c,"DELETE FROM appraisal_record WHERE id=10");
                }
                assertEquals(before,content(c));
            } finally {c.rollback();}
            assertEquals(0L,number(c,"SELECT count(*) FROM information_schema.schemata WHERE schema_name='"+schema+"'"));
            c.rollback();
        }
    }
    private String content(Connection c)throws SQLException {
        return text(c,"SELECT coalesce(jsonb_agg(to_jsonb(t)-'revision_required')::text,'[]') FROM (SELECT * FROM kpi_plan ORDER BY id) t")
            +text(c,"SELECT coalesce(jsonb_agg(to_jsonb(t)-'revision_required')::text,'[]') FROM (SELECT * FROM appraisal_record ORDER BY id) t");
    }
    private void sql(Connection c,String query)throws SQLException {try(var s=c.createStatement()){s.execute(query);}}
    private String text(Connection c,String query)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(query)){r.next();return r.getString(1);}}
    private long number(Connection c,String query)throws SQLException {return Long.parseLong(text(c,query));}
    private void reject(Connection c,String state,String query)throws SQLException {
        var savepoint=c.setSavepoint();
        try {assertEquals(state,assertThrows(SQLException.class,()->sql(c,query)).getSQLState());}
        finally {c.rollback(savepoint);c.releaseSavepoint(savepoint);}
    }
}
