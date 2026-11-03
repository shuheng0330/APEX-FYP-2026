package com.tbm.careerpathlearning.service.impl;

import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="APEX_PHASE2_POSTGRES_TEST",matches="true")
class KpiAssistanceRejectionMigrationPostgresTest {
    @Test void preservesExistingStatesAndAllowsFreshRequestsOnlyAfterRejection() throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        var schema="apex_assistance_rejection_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                sql(c,"CREATE TABLE staff (LIKE public.staff INCLUDING ALL)");sql(c,"INSERT INTO staff SELECT * FROM public.staff");
                var actor=text(c,"SELECT id FROM staff LIMIT 1");assertNotNull(actor);
                sql(c,"CREATE TABLE review_period_participant(id BIGINT PRIMARY KEY)");
                sql(c,"INSERT INTO review_period_participant VALUES(1),(2),(3)");
                var initial=new ClassPathResource("db/migration/annual-kpi/V36__individual_kpi_assistance.sql").getContentAsString(StandardCharsets.UTF_8);
                sql(c,initial.substring(0,initial.indexOf("ALTER TABLE kpi_plan")));
                sql(c,"INSERT INTO individual_kpi_assistance_authorization(owner_participant_id,superior_id,status,requested_at,authorized_at,authorized_by,consumed_at) VALUES "
                    +"(1,'"+actor+"','REQUESTED',now(),NULL,NULL,NULL),"
                    +"(2,'"+actor+"','AUTHORIZED',now(),now(),'"+actor+"',NULL),"
                    +"(3,'"+actor+"','CONSUMED',now(),now(),'"+actor+"',now())");
                var before=snapshot(c);
                sql(c,new ClassPathResource("db/migration/annual-kpi/V37__individual_kpi_assistance_rejection.sql").getContentAsString(StandardCharsets.UTF_8));
                assertEquals(before,snapshot(c));
                for(var reason:List.of("NULL","''","'   '","repeat('x',10001)")) {
                    var point=c.setSavepoint();
                    assertThrows(SQLException.class,()->sql(c,"UPDATE individual_kpi_assistance_authorization SET status='REJECTED',rejected_at=now(),rejected_by='"+actor+"',rejection_reason="+reason+" WHERE owner_participant_id=1"));
                    c.rollback(point);
                }
                sql(c,"UPDATE individual_kpi_assistance_authorization SET status='REJECTED',rejected_at=now(),rejected_by='"+actor+"',rejection_reason='Not needed' WHERE owner_participant_id=1");
                sql(c,"INSERT INTO individual_kpi_assistance_authorization(owner_participant_id,superior_id,requested_at) VALUES(1,'"+actor+"',now())");
                assertEquals("2",text(c,"SELECT count(*) FROM individual_kpi_assistance_authorization WHERE owner_participant_id=1"));
                var duplicate=c.setSavepoint();
                assertEquals("23505",assertThrows(SQLException.class,()->sql(c,"INSERT INTO individual_kpi_assistance_authorization(owner_participant_id,superior_id,requested_at) VALUES(1,'"+actor+"',now())")).getSQLState());
                c.rollback(duplicate);
                var inconsistent=c.setSavepoint();
                assertThrows(SQLException.class,()->sql(c,"UPDATE individual_kpi_assistance_authorization SET authorized_at=now(),authorized_by='"+actor+"' WHERE status='REJECTED'"));
                c.rollback(inconsistent);
                assertEquals("AUTHORIZED,CONSUMED",text(c,"SELECT string_agg(status,',' ORDER BY owner_participant_id) FROM individual_kpi_assistance_authorization WHERE owner_participant_id IN (2,3)"));
            } finally {c.rollback();}
        }
    }
    private String snapshot(Connection c)throws SQLException {
        return text(c,"SELECT jsonb_agg(to_jsonb(a)-ARRAY['rejected_at','rejected_by','rejection_reason'] ORDER BY id)::text FROM individual_kpi_assistance_authorization a");
    }
    private void sql(Connection c,String query)throws SQLException {try(var s=c.createStatement()){s.execute(query);}}
    private String text(Connection c,String query)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(query)){r.next();return r.getString(1);}}
}
