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
class IndividualKpiAssistanceMigrationPostgresTest {
    @ParameterizedTest @ValueSource(strings={"empty","populated","existing-column","wrong-type","untracked-table"})
    void forwardMigrationPreservesNormalPlansAndGrantsOrRefusesUntrackedSchema(String scenario) throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        var schema="apex_assistance_migration_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                for(var table:List.of("staff","authority","role_authority","review_period_participant","kpi_plan"))
                    sql(c,"CREATE TABLE "+table+" (LIKE public."+table+" INCLUDING ALL)");
                sql(c,"INSERT INTO staff SELECT * FROM public.staff");
                sql(c,"INSERT INTO review_period_participant SELECT * FROM public.review_period_participant");
                sql(c,"INSERT INTO authority OVERRIDING SYSTEM VALUE SELECT * FROM public.authority "
                    +"WHERE name NOT IN ('CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE','CAN_REVIEW_KPI_ASSESSMENT','CAN_MANAGE_ATTITUDE_CONFIGURATION')");
                sql(c,"INSERT INTO role_authority SELECT r.* FROM public.role_authority r JOIN authority a ON a.id=r.authority_id");
                sql(c,"SELECT setval(pg_get_serial_sequence('authority','id'),(SELECT max(id)+1 FROM authority),false)");
                if(!scenario.equals("empty")) sql(c,"INSERT INTO kpi_plan SELECT * FROM public.kpi_plan WHERE assistance_authorization_id IS NULL");
                sql(c,"ALTER TABLE kpi_plan DROP CONSTRAINT ck_assisted_kpi_plan");
                sql(c,"ALTER TABLE kpi_plan DROP CONSTRAINT ck_individual_submission_metadata");
                sql(c,"ALTER TABLE kpi_plan DROP COLUMN assistance_authorization_id");
                sql(c,"ALTER TABLE kpi_plan ADD CONSTRAINT ck_individual_submission_metadata CHECK (level <> 'INDIVIDUAL' OR status='DRAFT' OR "
                    +"(submitted_at IS NOT NULL AND submitted_by IS NOT NULL AND submitted_late IS NOT NULL AND submitted_to_superior_id IS NOT NULL))");
                if(scenario.equals("existing-column")) sql(c,"ALTER TABLE kpi_plan ADD COLUMN assistance_authorization_id BIGINT");
                if(scenario.equals("wrong-type")) sql(c,"ALTER TABLE kpi_plan ADD COLUMN assistance_authorization_id TEXT");
                if(scenario.equals("untracked-table")) sql(c,"CREATE TABLE individual_kpi_assistance_authorization(id BIGINT)");
                var before=planSnapshot(c);var grants=text(c,"SELECT coalesce(jsonb_agg(t)::text,'[]') FROM (SELECT * FROM role_authority ORDER BY role_id,authority_id) t");
                var migration=new ClassPathResource("db/migration/annual-kpi/V36__individual_kpi_assistance.sql").getContentAsString(StandardCharsets.UTF_8);
                if(scenario.equals("wrong-type") || scenario.equals("untracked-table")) {
                    var savepoint=c.setSavepoint();var error=assertThrows(SQLException.class,()->sql(c,migration));
                    assertTrue(error.getMessage().contains(scenario.equals("wrong-type")?"Incompatible untracked":"Untracked Individual"));
                    c.rollback(savepoint);
                    assertEquals(0L,number(c,"SELECT count(*) FROM authority WHERE name='CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE'"));
                } else {
                    sql(c,migration);
                    assertEquals(1L,number(c,"SELECT count(*) FROM authority WHERE name='CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE'"));
                    assertEquals(0L,number(c,"SELECT count(*) FROM individual_kpi_assistance_authorization"));
                    assertEquals(3L,number(c,"SELECT count(*) FROM pg_constraint WHERE conrelid='kpi_plan'::regclass "
                        +"AND conname IN ('fk_plan_assistance_scope','uq_plan_assistance','ck_assisted_kpi_plan')"));
                    assertEquals(2L,number(c,"SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() "
                        +"AND indexname IN ('idx_assistance_hr_queue','idx_assistance_superior')"));
                }
                assertEquals(before,planSnapshot(c));
                assertEquals(grants,text(c,"SELECT coalesce(jsonb_agg(t)::text,'[]') FROM (SELECT * FROM role_authority ORDER BY role_id,authority_id) t"));
            } finally {c.rollback();}
            assertEquals(0L,number(c,"SELECT count(*) FROM information_schema.schemata WHERE schema_name='"+schema+"'"));c.rollback();
        }
    }
    private String planSnapshot(Connection c)throws SQLException {
        return text(c,"SELECT coalesce(jsonb_agg(to_jsonb(t)-'assistance_authorization_id')::text,'[]') FROM (SELECT * FROM kpi_plan ORDER BY id) t");
    }
    private void sql(Connection c,String query)throws SQLException {try(var s=c.createStatement()){s.execute(query);}}
    private String text(Connection c,String query)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(query)){r.next();return r.getString(1);}}
    private long number(Connection c,String query)throws SQLException {return Long.parseLong(text(c,query));}
}
