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
class KpiReturnFeedbackMigrationPostgresTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void preservesExistingRowsWithoutFabricatingBaselines(boolean populated) throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        var schema="apex_return_feedback_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                sql(c,"CREATE TABLE staff(id UUID PRIMARY KEY)");sql(c,"INSERT INTO staff VALUES('00000000-0000-0000-0000-000000000001')");
                sql(c,"CREATE TABLE kpi_plan(id BIGINT PRIMARY KEY,return_reason TEXT,reviewed_at TIMESTAMPTZ,reviewed_by UUID)");
                if(populated) sql(c,"INSERT INTO kpi_plan VALUES(1,'Clarify target',now(),'00000000-0000-0000-0000-000000000001'),(2,NULL,NULL,NULL)");
                var before=text(c,"SELECT jsonb_agg(to_jsonb(p) ORDER BY id)::text FROM kpi_plan p");
                if(populated) {
                    sql(c,"ALTER TABLE kpi_plan ADD COLUMN latest_return_reason TEXT, ADD COLUMN latest_returned_at TIMESTAMPTZ, ADD COLUMN latest_returned_by UUID, ADD COLUMN latest_return_items JSONB");
                    sql(c,"UPDATE kpi_plan SET latest_return_reason='Existing feedback',latest_return_items='[]'::jsonb WHERE id=2");
                    var point=c.setSavepoint();
                    sql(c,"ALTER TABLE kpi_plan ALTER COLUMN latest_return_reason TYPE VARCHAR(10000)");
                    assertThrows(SQLException.class,()->sql(c,new ClassPathResource("db/migration/annual-kpi/V43__kpi_plan_latest_return_feedback.sql").getContentAsString(StandardCharsets.UTF_8)));
                    c.rollback(point);
                }
                sql(c,new ClassPathResource("db/migration/annual-kpi/V43__kpi_plan_latest_return_feedback.sql").getContentAsString(StandardCharsets.UTF_8));
                assertEquals(before,text(c,"SELECT jsonb_agg(to_jsonb(p)-'latest_return_reason'-'latest_returned_at'-'latest_returned_by'-'latest_return_items' ORDER BY id)::text FROM kpi_plan p"));
                if(populated) {
                    assertEquals("Clarify target",text(c,"SELECT latest_return_reason FROM kpi_plan WHERE id=1"));
                    assertNull(text(c,"SELECT latest_return_items::text FROM kpi_plan WHERE id=1"));
                    assertEquals("Existing feedback",text(c,"SELECT latest_return_reason FROM kpi_plan WHERE id=2"));
                    assertEquals("[]",text(c,"SELECT latest_return_items::text FROM kpi_plan WHERE id=2"));
                    var point=c.setSavepoint();
                    assertThrows(SQLException.class,()->sql(c,"UPDATE kpi_plan SET latest_return_items='{}'::jsonb WHERE id=1"));c.rollback(point);
                    sql(c,"UPDATE kpi_plan SET latest_return_items='[]'::jsonb WHERE id=1");
                }
            } finally {c.rollback();}
        }
    }
    private void sql(Connection c,String q)throws SQLException {try(var s=c.createStatement()){s.execute(q);}}
    private String text(Connection c,String q)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(q)){r.next();return r.getString(1);}}
}
