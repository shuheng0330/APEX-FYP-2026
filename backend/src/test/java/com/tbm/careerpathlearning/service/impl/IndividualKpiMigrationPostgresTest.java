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
class IndividualKpiMigrationPostgresTest {
    @ParameterizedTest @ValueSource(strings={"draft","existing-column","wrong-type","unrouted-pending"})
    void migrationPreservesDraftsAndRefusesAmbiguousHistory(String scenario) throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        var schema="apex_individual_migration_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                for(var table:List.of("staff","authority","role_authority","kpi_plan"))
                    sql(c,"CREATE TABLE "+table+" (LIKE public."+table+" INCLUDING ALL)");
                sql(c,"INSERT INTO staff SELECT * FROM public.staff");
                sql(c,"INSERT INTO authority OVERRIDING SYSTEM VALUE SELECT * FROM public.authority "
                    +"WHERE name <> 'CAN_REVIEW_INDIVIDUAL_KPI'");
                sql(c,"INSERT INTO role_authority SELECT r.* FROM public.role_authority r JOIN authority a ON a.id=r.authority_id");
                sql(c,"SELECT setval(pg_get_serial_sequence('authority','id'),(SELECT max(id)+1 FROM authority),false)");
                sql(c,"INSERT INTO kpi_plan SELECT * FROM public.kpi_plan WHERE level <> 'INDIVIDUAL' OR status='DRAFT'");
                sql(c,"SELECT setval(pg_get_serial_sequence('kpi_plan','id'),"
                    +"(SELECT coalesce(max(id),0)+1 FROM kpi_plan),false)");
                for(var name:List.of("ck_individual_submission_metadata","ck_individual_review_metadata","ck_individual_return_reason"))
                    sql(c,"ALTER TABLE kpi_plan DROP CONSTRAINT IF EXISTS "+name);
                sql(c,"ALTER TABLE kpi_plan DROP COLUMN IF EXISTS submitted_to_superior_id");
                if(scenario.equals("existing-column")) sql(c,"ALTER TABLE kpi_plan ADD COLUMN submitted_to_superior_id UUID");
                if(scenario.equals("wrong-type")) sql(c,"ALTER TABLE kpi_plan ADD COLUMN submitted_to_superior_id TEXT");
                if(scenario.equals("unrouted-pending")) sql(c,"INSERT INTO kpi_plan (review_period_id,level,owner_participant_id,status,"
                    +"created_at,updated_at,created_by,updated_by) SELECT 1,'INDIVIDUAL',7,'PENDING_APPROVAL',"
                    +"now(),now(),id,id FROM staff LIMIT 1");
                var before=snapshot(c);
                var migration=new ClassPathResource("db/migration/annual-kpi/V34__individual_kpi_plan_review.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
                if(scenario.equals("wrong-type") || scenario.equals("unrouted-pending")) {
                    var savepoint=c.setSavepoint();
                    var error=assertThrows(SQLException.class,()->sql(c,migration));
                    assertTrue(error.getMessage().contains(scenario.equals("wrong-type") ? "Incompatible untracked" : "need explicit review routing"));
                    c.rollback(savepoint);
                    assertEquals(0L,number(c,"SELECT count(*) FROM authority WHERE name='CAN_REVIEW_INDIVIDUAL_KPI'"));
                } else {
                    sql(c,migration);
                    assertEquals(1L,number(c,"SELECT count(*) FROM authority WHERE name='CAN_REVIEW_INDIVIDUAL_KPI'"));
                    assertEquals(4L,number(c,"SELECT count(*) FROM pg_constraint WHERE conrelid='kpi_plan'::regclass AND conname IN "
                        +"('fk_individual_kpi_superior','ck_individual_submission_metadata','ck_individual_review_metadata',"
                        +"'ck_individual_return_reason')"));
                    assertEquals(1L,number(c,"SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() "
                        +"AND indexname='idx_individual_kpi_review_queue'"));
                }
                assertEquals(before,snapshot(c));
            } finally {c.rollback();}
            assertEquals(0L,number(c,"SELECT count(*) FROM information_schema.schemata WHERE schema_name='"+schema+"'"));
            c.rollback();
        }
    }
    private String snapshot(Connection c)throws SQLException {
        return text(c,"SELECT coalesce(jsonb_agg(to_jsonb(t)-'submitted_to_superior_id')::text,'[]') "
            +"FROM (SELECT * FROM kpi_plan ORDER BY id) t");
    }
    private void sql(Connection c,String query)throws SQLException {try(var s=c.createStatement()){s.execute(query);}}
    private String text(Connection c,String query)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(query)){r.next();return r.getString(1);}}
    private long number(Connection c,String query)throws SQLException {return Long.parseLong(text(c,query));}
}
