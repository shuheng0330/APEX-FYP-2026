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
class KpiAssistanceRequestReasonMigrationPostgresTest {
    @ParameterizedTest @ValueSource(booleans={false,true})
    void addsReasonWithoutChangingEarlierRequests(boolean populated) throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        var schema="apex_assistance_reason_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                sql(c,"CREATE TABLE staff (LIKE public.staff INCLUDING ALL)");sql(c,"INSERT INTO staff SELECT * FROM public.staff");
                var actor=text(c,"SELECT id FROM staff LIMIT 1");assertNotNull(actor);
                sql(c,"CREATE TABLE review_period_participant(id BIGINT PRIMARY KEY)");
                sql(c,"INSERT INTO review_period_participant VALUES(1),(2),(3),(4),(5)");
                var initial=resource("V36__individual_kpi_assistance.sql");
                sql(c,initial.substring(0,initial.indexOf("ALTER TABLE kpi_plan")));
                sql(c,resource("V37__individual_kpi_assistance_rejection.sql"));
                if(populated) {
                    sql(c,"INSERT INTO individual_kpi_assistance_authorization(owner_participant_id,superior_id,status,requested_at,authorized_at,authorized_by,consumed_at) VALUES "
                        +"(1,'"+actor+"','REQUESTED',now(),NULL,NULL,NULL),"
                        +"(2,'"+actor+"','AUTHORIZED',now(),now(),'"+actor+"',NULL),"
                        +"(3,'"+actor+"','CONSUMED',now(),now(),'"+actor+"',now())");
                    sql(c,"INSERT INTO individual_kpi_assistance_authorization(owner_participant_id,superior_id,status,requested_at,rejected_at,rejected_by,rejection_reason) VALUES "
                        +"(4,'"+actor+"','REJECTED',now(),now(),'"+actor+"','Discuss targets')");
                }
                // Reconcile development Hibernate's column without losing already-recorded text.
                if(populated) {
                    sql(c,"ALTER TABLE individual_kpi_assistance_authorization ADD COLUMN request_reason TEXT");
                    sql(c,"UPDATE individual_kpi_assistance_authorization SET request_reason='Previously recorded reason' WHERE owner_participant_id=2");
                    var incompatible=c.setSavepoint();
                    sql(c,"ALTER TABLE individual_kpi_assistance_authorization ALTER COLUMN request_reason TYPE VARCHAR(10000)");
                    assertThrows(SQLException.class,()->sql(c,resource("V38__individual_kpi_assistance_request_reason.sql")));
                    c.rollback(incompatible);
                }
                var before=text(c,"SELECT jsonb_agg(to_jsonb(a)-'request_reason' ORDER BY id)::text FROM individual_kpi_assistance_authorization a");
                sql(c,resource("V38__individual_kpi_assistance_request_reason.sql"));
                assertEquals(before,text(c,"SELECT jsonb_agg(to_jsonb(a)-'request_reason' ORDER BY id)::text FROM individual_kpi_assistance_authorization a"));
                assertEquals(populated?"3":"0",text(c,"SELECT count(*) FROM individual_kpi_assistance_authorization WHERE request_reason IS NULL"));
                if(populated) assertEquals("Previously recorded reason",text(c,"SELECT request_reason FROM individual_kpi_assistance_authorization WHERE owner_participant_id=2"));
                for(var reason:List.of("''","'   '","E'\\t\\n'","repeat('x',10001)")) {
                    var point=c.setSavepoint();
                    assertThrows(SQLException.class,()->sql(c,"INSERT INTO individual_kpi_assistance_authorization(owner_participant_id,superior_id,requested_at,request_reason) VALUES(5,'"+actor+"',now(),"+reason+")"));
                    c.rollback(point);
                }
                sql(c,"INSERT INTO individual_kpi_assistance_authorization(owner_participant_id,superior_id,requested_at,request_reason) VALUES(5,'"+actor+"',now(),'Needs help preparing measurable KPIs')");
                assertEquals("Needs help preparing measurable KPIs",text(c,"SELECT request_reason FROM individual_kpi_assistance_authorization WHERE owner_participant_id=5"));
            } finally {c.rollback();}
        }
    }
    private String resource(String name)throws Exception {return new ClassPathResource("db/migration/annual-kpi/"+name).getContentAsString(StandardCharsets.UTF_8);}
    private void sql(Connection c,String query)throws SQLException {try(var s=c.createStatement()){s.execute(query);}}
    private String text(Connection c,String query)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(query)){r.next();return r.getString(1);}}
}
