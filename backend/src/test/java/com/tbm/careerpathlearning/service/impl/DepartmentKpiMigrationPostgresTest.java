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
class DepartmentKpiMigrationPostgresTest {
    @ParameterizedTest @ValueSource(strings={"fresh-empty","fresh-populated","hibernate-populated","partial","wrong-type"})
    void migrationPreservesDataOrSafelyRefusesUntrackedColumns(String scenario) throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        var schema="apex_department_migration_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                for(var table:List.of("staff","authority","role_authority","kpi_plan"))
                    sql(c,"CREATE TABLE "+table+" (LIKE public."+table+" INCLUDING ALL)");
                sql(c,"INSERT INTO staff SELECT * FROM public.staff");
                sql(c,"INSERT INTO authority OVERRIDING SYSTEM VALUE SELECT * FROM public.authority "
                    +"WHERE name NOT IN ('CAN_MANAGE_DEPARTMENT_KPI','CAN_APPROVE_DEPARTMENT_KPI','CAN_REVIEW_INDIVIDUAL_KPI',"
                    +"'CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE','CAN_REVIEW_KPI_ASSESSMENT')");
                sql(c,"INSERT INTO role_authority SELECT r.* FROM public.role_authority r JOIN authority a ON a.id=r.authority_id");
                sql(c,"SELECT setval(pg_get_serial_sequence('authority','id'),(SELECT max(id)+1 FROM authority),false)");
                if(!scenario.equals("fresh-empty")) {
                    // Pre-V33 fixtures cannot contain plans requiring later submission metadata.
                    var filter=scenario.equals("hibernate-populated") ? "" : " WHERE level='COMPANY' OR status='DRAFT'";
                    sql(c,"INSERT INTO kpi_plan SELECT * FROM public.kpi_plan"+filter);
                }
                for(var name:List.of("ck_department_submission_metadata","ck_department_review_metadata","ck_department_return_reason"))
                    sql(c,"ALTER TABLE kpi_plan DROP CONSTRAINT IF EXISTS "+name);
                if(!scenario.equals("hibernate-populated")) {
                    for(var name:List.of("submitted_at","submitted_by","submitted_late","reviewed_at","reviewed_by","reviewed_late","return_reason"))
                        sql(c,"ALTER TABLE kpi_plan DROP COLUMN "+name);
                    if(scenario.equals("partial")) sql(c,"ALTER TABLE kpi_plan ADD COLUMN submitted_at TIMESTAMPTZ");
                    if(scenario.equals("wrong-type")) {
                        sql(c,"ALTER TABLE kpi_plan ADD COLUMN submitted_at TIMESTAMPTZ, ADD COLUMN submitted_by UUID, ADD COLUMN submitted_late BOOLEAN, "
                            +"ADD COLUMN reviewed_at TIMESTAMPTZ, ADD COLUMN reviewed_by UUID, ADD COLUMN reviewed_late TEXT, ADD COLUMN return_reason TEXT");
                    }
                }
                var before=planSnapshot(c);var grants=text(c,"SELECT coalesce(json_agg(t)::text,'[]') FROM (SELECT * FROM role_authority ORDER BY role_id,authority_id) t");
                var migration=new ClassPathResource("db/migration/annual-kpi/V33__department_kpi_plan_review.sql").getContentAsString(StandardCharsets.UTF_8);
                if(scenario.equals("partial") || scenario.equals("wrong-type")) {
                    var savepoint=c.setSavepoint();
                    assertTrue(assertThrows(SQLException.class,()->sql(c,migration)).getMessage().contains("Incompatible or partial"));
                    c.rollback(savepoint);
                    assertEquals(0L,number(c,"SELECT count(*) FROM authority WHERE name IN ('CAN_MANAGE_DEPARTMENT_KPI','CAN_APPROVE_DEPARTMENT_KPI')"));
                } else {
                    sql(c,migration);
                    assertEquals(2L,number(c,"SELECT count(*) FROM authority WHERE name IN ('CAN_MANAGE_DEPARTMENT_KPI','CAN_APPROVE_DEPARTMENT_KPI')"));
                    assertEquals(5L,number(c,"SELECT count(*) FROM pg_constraint WHERE conrelid='kpi_plan'::regclass "
                        +"AND conname IN ('fk_kpi_plan_submitter','fk_kpi_plan_reviewer','ck_department_submission_metadata','ck_department_review_metadata','ck_department_return_reason')"));
                }
                assertEquals(before,planSnapshot(c));
                assertEquals(grants,text(c,"SELECT coalesce(json_agg(t)::text,'[]') FROM (SELECT * FROM role_authority ORDER BY role_id,authority_id) t"));
            } finally {c.rollback();}
            assertEquals(0L,number(c,"SELECT count(*) FROM information_schema.schemata WHERE schema_name='"+schema+"'"));
            c.rollback();
        }
    }
    private String planSnapshot(Connection c)throws SQLException {
        return text(c,"SELECT coalesce(jsonb_agg(to_jsonb(t)-ARRAY['submitted_at','submitted_by','submitted_late',"
            +"'reviewed_at','reviewed_by','reviewed_late','return_reason'])::text,'[]') FROM (SELECT * FROM kpi_plan ORDER BY id) t");
    }
    private void sql(Connection c,String sql)throws SQLException {try(var s=c.createStatement()){s.execute(sql);}}
    private String text(Connection c,String sql)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    private long number(Connection c,String sql)throws SQLException {return Long.parseLong(text(c,sql));}
}
