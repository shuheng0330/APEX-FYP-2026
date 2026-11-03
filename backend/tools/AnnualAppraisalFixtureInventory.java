import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Read-only pre-approval inventory. No fixture creation, login or data changes. */
public class AnnualAppraisalFixtureInventory {
    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !"--inspect".equals(args[0])) throw new IllegalArgumentException("Only --inspect is supported; no data-writing mode exists");
        var env=Dotenv.configure().directory(".").load();
        var source=URI.create(env.get("DB_URL").substring(5));
        if(!Set.of("localhost","127.0.0.1").contains(source.getHost()) || source.getQuery()!=null) throw new IllegalStateException("Local database required");
        String db="apex_central_results_uat";
        String url="jdbc:postgresql://"+source.getHost()+":"+source.getPort()+"/"+db;
        var report=new LinkedHashMap<String,Object>();
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);c.setReadOnly(true);
            var marker=rows(c,"SELECT shobj_description(oid,'pg_database') marker FROM pg_database WHERE datname=current_database()");
            if(!"APEX disposable central KPI results UAT clone".equals(marker.get(0).get("marker"))) throw new IllegalStateException("UAT marker mismatch");
            report.put("database",db);
            report.put("fixtureActors",rows(c,"SELECT s.id,s.name,s.email,s.account_status,s.role_id,a.name authority FROM staff s LEFT JOIN role_authority ra ON ra.role_id=s.role_id LEFT JOIN authority a ON a.id=ra.authority_id WHERE s.email IN ('uat.central.setup@example.test','uat.central.md@example.test','thongshuheng0330@gmail.com','alice.johnson@apex.dev','bob.smith@apex.dev','david.lim@apex.dev') ORDER BY s.email,a.name"));
            report.put("periods",rows(c,"SELECT * FROM annual_kpi_review_period ORDER BY id"));
            report.put("participants",rows(c,"SELECT p.*,s.email,m.name current_manager FROM review_period_participant p JOIN staff s ON s.id=p.staff_id LEFT JOIN staff m ON m.id=s.manager_id WHERE p.review_period_id=7 ORDER BY p.id"));
            report.put("checkpoints",rows(c,"SELECT * FROM review_checkpoint WHERE review_period_id=7 ORDER BY review_frequency,sequence_number"));
            report.put("allocations",rows(c,"SELECT * FROM review_period_employee_level_configuration WHERE review_period_id=7 ORDER BY id"));
            report.put("plans",rows(c,"SELECT id,level,status,current_version_id,owner_participant_id,department_id FROM kpi_plan WHERE review_period_id=7 ORDER BY id"));
            report.put("assignments",rows(c,"SELECT a.* FROM employee_kpi_assignment a JOIN review_period_participant p ON p.id=a.participant_id WHERE p.review_period_id=7 ORDER BY a.id"));
            report.put("assessments",rows(c,"SELECT * FROM kpi_assessment WHERE review_period_id=7 ORDER BY id"));
            report.put("centralResults",rows(c,"SELECT * FROM kpi_central_result WHERE review_period_id=7 ORDER BY id"));
            report.put("attitudes",rows(c,"SELECT * FROM attitude_assessment WHERE review_period_id=7 ORDER BY id"));
            report.put("attitudeMappings",rows(c,"SELECT m.* FROM attitude_role_format_mapping m JOIN annual_kpi_review_period p ON p.attitude_configuration_id=m.configuration_id WHERE p.id=7"));
            report.put("attitudeCriteria",rows(c,"SELECT a.* FROM attitude_criterion a JOIN annual_kpi_review_period p ON p.attitude_configuration_id=a.configuration_id WHERE p.id=7 ORDER BY a.id"));
            report.put("annualAppraisals",rows(c,"SELECT id,participant_id,review_period_id,employee_id,status FROM annual_appraisal ORDER BY id"));
            report.put("legacyAppraisals",rows(c,"SELECT a.id,a.staff_id,a.status FROM appraisal_record a ORDER BY a.id"));
            c.rollback();
        }
        var path=Path.of("target","annual-appraisal-uat","fixture-inventory.json");Files.createDirectories(path.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(path.toFile(),report);
        for(var e:report.entrySet()) System.out.println(e.getKey()+": "+(e.getValue() instanceof List<?> list?list.size():e.getValue()));
        System.out.println("Read-only inventory: "+path.toAbsolutePath());
    }
    private static List<Map<String,String>> rows(Connection c,String sql) throws Exception {
        var result=new ArrayList<Map<String,String>>();
        try(var s=c.createStatement();var rs=s.executeQuery(sql)) {
            while(rs.next()) {var row=new LinkedHashMap<String,String>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++) row.put(rs.getMetaData().getColumnLabel(i),rs.getString(i));result.add(row);}
        }
        return result;
    }
}
