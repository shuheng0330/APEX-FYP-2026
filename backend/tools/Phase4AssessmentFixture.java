import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbm.careerpathlearning.CareerPathLearningBackendApplication;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.service.*;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** Explicit, opt-in fixture. Never registered as an application bean or migration. */
public class Phase4AssessmentFixture {
    static final String DB="apex_central_results_uat", MARKER="APEX disposable central KPI results UAT clone";
    static final String SETUP="uat.central.setup@example.test", MD="uat.central.md@example.test";
    static final String ALICE="alice.johnson@apex.dev", BOB="bob.smith@apex.dev", DAVID="david.lim@apex.dev";
    static final Path STATE=Path.of("target/annual-appraisal-uat/phase4-fixture");
    static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules();
    final Dotenv env=Dotenv.configure().directory(".").load();
    final URI source=URI.create(env.get("DB_URL").substring(5));
    final String url="jdbc:postgresql://"+source.getHost()+":"+source.getPort()+"/"+DB;
    ConfigurableApplicationContext app;
    Map<String,Object> manifest=new LinkedHashMap<>();
    record Actor(UUID id,List<String> permissions) {}

    public static void main(String[] args) throws Exception {
        if(args.length!=1 || !Set.of("--plan","--apply","--verify").contains(args[0]))
            throw new IllegalArgumentException("Use --plan, --apply or --verify; marked isolated UAT only");
        var fixture=new Phase4AssessmentFixture();fixture.run(args[0]);
    }
    void run(String mode) throws Exception {
        if(!Set.of("localhost","127.0.0.1").contains(source.getHost()) || source.getPort()<1
                || source.getQuery()!=null || source.getPath().equals("/"+DB)) throw new IllegalStateException("Separate local source database required");
        Files.createDirectories(STATE);
        try(var c=connection(false)) {
            c.setReadOnly(true);
            for(var email:List.of(SETUP,MD,ALICE,BOB,DAVID)) {
                var a=actor(c,email);System.out.println(email+": active; "+a.permissions().size()+" existing permissions");
            }
            requireAuthority(c,SETUP,"CAN_MANAGE_ANNUAL_KPI_REVIEW_PERIOD");
            requireAuthority(c,MD,"CAN_MANAGE_COMPANY_KPI");requireAuthority(c,MD,"CAN_APPROVE_DEPARTMENT_KPI");
            for(var p:List.of("CAN_MANAGE_DEPARTMENT_KPI","CAN_REVIEW_INDIVIDUAL_KPI","CAN_REVIEW_KPI_ASSESSMENT","CAN_REVIEW_ATTITUDE_EVALUATION","CAN_SUBMIT_ANNUAL_APPRAISAL")) requireAuthority(c,ALICE,p);
            requireAuthority(c,DAVID,"CAN_REVIEW_ANNUAL_APPRAISAL");
            check("OPEN/CENTRAL_RESULTS".equals(scalar(c,"SELECT status||'/'||assessment_model FROM annual_kpi_review_period WHERE id=7")),"Period 7 must remain Open / Central Results");
            check("24".equals(scalar(c,"SELECT p.id FROM review_period_participant p JOIN staff s ON s.id=p.staff_id JOIN staff m ON m.id=s.manager_id WHERE p.review_period_id=7 AND s.email=? AND m.email=?",BOB,ALICE)),"Bob's recorded/current scope changed");
            check("1".equals(scalar(c,"SELECT count(*) FROM review_checkpoint WHERE review_period_id=7 AND review_frequency='QUARTERLY'")),"Existing quarterly schedule changed");
            check("0".equals(scalar(c,"SELECT count(*) FROM kpi_assessment WHERE participant_id=23")),"Carol must remain incomplete");
            System.out.println("Approved scope: Bob participant 24 / period 7, plus [UAT SIMULATED] 2023 and 2024 Annual Review. Carol remains incomplete.");
            if(mode.equals("--plan")) { System.out.println("READ ONLY: no Spring startup, backup or fixture writes");return; }
            if(mode.equals("--apply") && !Files.exists(STATE.resolve("manifest.json"))) {
                check("0".equals(scalar(c,"SELECT count(*) FROM kpi_plan WHERE review_period_id=7")),"Period 7 already has plans; stop rather than overwrite");
                check("0".equals(scalar(c,"SELECT count(*) FROM annual_appraisal WHERE participant_id=24")),"Bob already has a current appraisal; stop for review");
                check("0".equals(scalar(c,"SELECT count(*) FROM annual_kpi_review_period WHERE name LIKE '[UAT SIMULATED]%'")),"Untracked simulated history exists; inspect first");
            }
        }
        boolean create=mode.equals("--apply") && !Files.exists(STATE.resolve("manifest.json"));
        Map<String,List<String>> sourceBefore,uatBefore;
        try(var c=connection(true)){c.setReadOnly(true);sourceBefore=snapshot(c);}
        try(var c=connection(false)){c.setReadOnly(true);uatBefore=snapshot(c);}
        if(create) {
            JSON.writeValue(STATE.resolve("source-before.json").toFile(),sourceBefore);
            JSON.writeValue(STATE.resolve("uat-before.json").toFile(),uatBefore);
            backup();
        } else check(Files.exists(STATE.resolve("manifest.json")),"Apply the approved fixture first");
        start();
        try {
            var tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
            tx.setTimeout(240);
            if(create) {
                tx.execute(status->{
                    try {
                        manifest.put("database",DB);manifest.put("createdAt",Instant.now().toString());
                        manifest.put("current",complete(7L,"[UAT P4 BOB]",4,4,4,5,new int[]{4,4,5},false));
                        manifest.put("2023",history(2023,3,3,3,4,new int[]{3,4,4}));
                        manifest.put("2024",history(2024,4,3,4,4,new int[]{4,4,4}));
                        verify();return null;
                    } catch(Exception e) {throw new IllegalStateException("Fixture rolled back; no readiness/status shortcuts were used",e);}
                });
                JSON.writerWithDefaultPrettyPrinter().writeValue(STATE.resolve("manifest.json").toFile(),manifest);
            } else {
                manifest=JSON.readValue(STATE.resolve("manifest.json").toFile(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
                tx.setReadOnly(true);tx.execute(status->{verify();return null;});
            }
            verifyHttp();
        } finally {SecurityContextHolder.clearContext();app.close();}
        try(var c=connection(true)){c.setReadOnly(true);check(sourceBefore.equals(snapshot(c)),"Development database fingerprints changed; investigate");}
        try(var c=connection(false)) {
            c.setReadOnly(true);var after=snapshot(c);
            for(var entry:uatBefore.entrySet()) check(after.getOrDefault(entry.getKey(),List.of()).containsAll(entry.getValue()),"A pre-existing UAT row changed: "+entry.getKey());
            if(!create) check(uatBefore.equals(after),"Verification or repeat apply changed UAT rows");
        }
        System.out.println("PASS: Bob ready; Carol waiting; simulated history verified; existing UAT rows and development database unchanged.");
        System.out.println(create?"Created approved fixture. Manifest: "+STATE.resolve("manifest.json").toAbsolutePath():"Read-only verification / idempotent repeat: no new fixture writes.");
    }
    void start() {
        System.setProperty("spring.devtools.restart.enabled","false");
        var boot=new SpringApplication(CareerPathLearningBackendApplication.class);
        // This maintenance process must not run schedulers against unrelated periods.
        boot.addInitializers(context->context.addBeanFactoryPostProcessor(factory->{
            var registry=(BeanDefinitionRegistry)factory;
            String scheduler="org.springframework.context.annotation.internalScheduledAnnotationProcessor";
            if(registry.containsBeanDefinition(scheduler)) registry.removeBeanDefinition(scheduler);
        }));
        app=boot.run("--spring.profiles.active=dev","--server.address=127.0.0.1","--server.port=0","--server.ssl.enabled=false",
            "--spring.datasource.url="+url,"--spring.datasource.username="+env.get("DB_USER"),"--spring.datasource.password="+env.get("DB_PASS"),
            "--spring.jpa.hibernate.ddl-auto=validate","--spring.flyway.enabled=false","--spring.jpa.open-in-view=false",
            "--spring.mail.host=127.0.0.1","--spring.mail.port=1025","--spring.mail.properties.mail.smtp.auth=false",
            "--spring.mail.properties.mail.smtp.starttls.enable=false","--spring.mail.properties.mail.smtp.starttls.required=false",
            "--spring.mail.properties.mail.debug=false","--logging.level.root=WARN",
            "--logging.level.org.springframework.boot.context.config=WARN","--logging.level.me.paulschwarz.springdotenv=WARN");
    }
    Map<String,Object> history(int year,int company,int department,int first,int second,int[] attitude) throws Exception {
        var request=new AnnualKpiReviewPeriodRequest();var existing=bean(AnnualKpiReviewPeriodService.class).get(7L);
        request.setName("[UAT SIMULATED] "+year+" Annual Review");
        request.setStartDate(LocalDate.of(year,1,1));request.setEndDate(LocalDate.of(year,12,31));
        request.setKpiSetupDeadline(request.getStartDate());request.setSelfAssessmentDaysAfterCheckpoint(7);request.setSuperiorAssessmentDaysAfterSelfDeadline(7);
        request.setAttitudeSelfAssessmentStartDate(LocalDate.of(year,10,1));request.setAttitudeSelfAssessmentDeadline(LocalDate.of(year,12,15));
        request.setSuperiorAttitudeEvaluationDeadline(LocalDate.of(year,12,22));request.setAppraisalRecommendationDeadline(LocalDate.of(year+1,1,21));
        request.setHrFinalisationDeadline(LocalDate.of(year+1,1,28));
        request.setKpiPerformanceWeight(existing.getKpiPerformanceWeight());request.setAttitudeEvaluationWeight(existing.getAttitudeEvaluationWeight());
        request.setAnnualKpiConsolidationMethod(existing.getAnnualKpiConsolidationMethod());
        request.setEmployeeLevelConfigurations(existing.getEmployeeLevelConfigurations().stream().map(level->{
            var copy=new ReviewPeriodEmployeeLevelConfigurationDto();copy.setEmployeeLevelId(level.getEmployeeLevelId());
            copy.setCompanyKpiWeight(level.getCompanyKpiWeight());copy.setDepartmentKpiWeight(level.getDepartmentKpiWeight());copy.setIndividualKpiWeight(level.getIndividualKpiWeight());return copy;
        }).toList());
        request.setRoleConfigurations(existing.getRoleConfigurations().stream().map(role->{
            var copy=new AnnualKpiReviewPeriodRequest.RoleFrequency();copy.setRoleId(role.getRoleId());copy.setReviewFrequency(role.getReviewFrequency());return copy;
        }).toList());
        var period=as(SETUP,id->bean(AnnualKpiReviewPeriodService.class).create(request,true,id));
        check(period.getStatus()==AnnualKpiReviewPeriodStatus.OPEN,"Historical fixture must open through normal publication");
        check(period.getAttitudeConfigurationId()!=null,"Published attitude configuration missing");
        return complete(period.getId(),"[UAT SIMULATED "+year+"]",company,department,first,second,attitude,true);
    }
    Map<String,Object> complete(Long periodId,String prefix,int companyPoint,int departmentPoint,int firstPoint,int secondPoint,int[] attitudePoints,boolean approve) throws Exception {
        System.out.println("Preparing "+prefix+" via normal plan, assessment and approval services");
        Long participant=participant(periodId,BOB);var plans=bean(KpiPlanService.class);
        var company=as(MD,id->plans.createCompany(plan(periodId,null,null,List.of(kpi(prefix+" Revenue attainment","Financial","Revenue","Achieve 100% of approved revenue budget","% of budget",100,
            List.of("Below 80% of budget","80% to below 90% of budget","90% to below 100% of budget","100% to below 110% of budget","At least 110% of budget")))),id));
        company=as(MD,id->plans.publishCompany(companyId(periodId),id));
        var department=as(ALICE,id->plans.createDepartment(plan(periodId,2L,null,List.of(kpi(prefix+" Service availability","Operations","IT reliability","Maintain 99.5% service availability","% uptime",100,
            List.of("Below 97% uptime","97% to below 98.5% uptime","98.5% to below 99.5% uptime","99.5% to below 99.9% uptime","At least 99.9% uptime")))),id));
        Long deptId=department.getId();as(ALICE,id->plans.submitDepartment(deptId,id));department=as(MD,id->plans.approveDepartment(deptId,id));
        var individual=as(BOB,id->plans.createIndividual(plan(periodId,null,participant,List.of(
            kpi(prefix+" Delivery quality","Technical","Delivery","Deliver 90% of committed work on time","% on-time",60,
                List.of("Below 60% on-time","60% to below 75% on-time","75% to below 90% on-time","90% to below 98% on-time","At least 98% on-time")),
            kpi(prefix+" Defect prevention","Technical","Quality","No more than 3 escaped defects per checkpoint","defects",40,
                List.of("More than 8 escaped defects","6 to 8 escaped defects","4 to 5 escaped defects","2 to 3 escaped defects","0 to 1 escaped defects")))),id));
        Long indId=individual.getId();as(BOB,id->plans.submitIndividual(indId,id));individual=as(ALICE,id->plans.approveIndividual(indId,id));
        var checkpoints=bean(AnnualKpiReviewPeriodService.class).get(periodId).getCheckpoints().stream().filter(c->c.getReviewFrequency()==ReviewFrequency.QUARTERLY).toList();
        check(checkpoints.size()==(approve?4:1),"Unexpected quarterly checkpoint count");
        var assessmentIds=new ArrayList<Long>();
        for(var cp:checkpoints) {
            publishCentral(company,cp.getId(),MD,companyPoint,prefix,companyPoint==4?"104% of approved revenue budget":"96% of approved revenue budget");
            publishCentral(department,cp.getId(),ALICE,departmentPoint,prefix,departmentPoint==4?"99.7% measured service availability":"99.1% measured service availability");
            var assessmentService=bean(KpiAssessmentService.class);var virtual=as(BOB,id->assessmentService.mine(cp.getId(),id));
            check(virtual.getId()==null,"Refusing to replace a started KPI assessment");
            var request=new KpiAssessmentRequest();request.setCheckpointId(cp.getId());request.setPlanVersionIds(virtual.getPlanVersionIds());
            request.setItems(virtual.getItems().stream().map(item->{var answer=new KpiAssessmentRequest.Answer();answer.setAssignmentId(item.getAssignmentId());
                answer.setSelfPoint(4);answer.setSelfComment(prefix+" Delivered planned releases and documented test outcomes for "+cp.getEndDate()+".");return answer;}).toList());
            var saved=as(BOB,id->assessmentService.create(request,id));Long assessmentId=saved.getId();as(BOB,id->assessmentService.submit(assessmentId,id));
            var superiorRequest=new KpiSuperiorAssessmentRequest();var loaded=as(ALICE,id->assessmentService.get(assessmentId,id));
            superiorRequest.setItems(loaded.getItems().stream().map(item->{var answer=new KpiSuperiorAssessmentRequest.Answer();answer.setItemId(item.getId());
                boolean delivery=item.getKpi().getName().contains("Delivery quality");answer.setSuperiorPoint(delivery?firstPoint:secondPoint);
                answer.setSuperiorComment(prefix+(delivery?" Met delivery commitments with clear release communication.":" Effective regression testing reduced escaped defects; maintain the same discipline."));return answer;}).toList());
            as(ALICE,id->assessmentService.saveSuperiorDraft(assessmentId,superiorRequest,id));var reviewed=as(ALICE,id->assessmentService.completeReview(assessmentId,id));
            check(reviewed.getStatus()==KpiAssessmentStatus.REVIEWED,"KPI review did not complete");assessmentIds.add(assessmentId);
        }
        var attitudes=bean(AttitudeAssessmentService.class);var virtual=as(BOB,id->attitudes.mine(periodId,id));
        check(virtual.getId()==null && virtual.isAvailable(),"Attitude evaluation is unavailable or already started");
        check(virtual.getItems().size()==3,"Bob's applicable attitude criteria changed");
        var request=new AttitudeAssessmentRequest();request.setReviewPeriodId(periodId);
        request.setItems(virtual.getItems().stream().map(item->{var a=new AttitudeAssessmentRequest.Answer();a.setCriterionId(item.getCriterionId());
            a.setSelfPoint(item.getName().equals("Respect")?5:4);a.setSelfComment(prefix+" Shared progress openly, supported colleagues and followed through on agreed actions.");return a;}).toList());
        var saved=as(BOB,id->attitudes.create(request,id));Long attitudeId=saved.getId();as(BOB,id->attitudes.submit(attitudeId,id));
        var submitted=as(ALICE,id->attitudes.get(attitudeId,id));var superiorRequest=new AttitudeSuperiorAssessmentRequest();
        superiorRequest.setItems(submitted.getItems().stream().map(item->{var a=new AttitudeSuperiorAssessmentRequest.Answer();a.setItemId(item.getId());
            int index=switch(item.getName()){case "Integrity"->0;case "Respect"->1;case "Taking Initiative"->2;default->throw new IllegalStateException("Unexpected criterion "+item.getName());};
            a.setSuperiorPoint(attitudePoints[index]);a.setSuperiorComment(prefix+switch(index){case 0->" Reliable and transparent when reporting delivery risks.";case 1->" Listens to colleagues and provides constructive support.";default->" Proactively improved release checks and shared the approach with the team.";});return a;}).toList());
        as(ALICE,id->attitudes.saveSuperiorDraft(attitudeId,superiorRequest,id));as(ALICE,id->attitudes.completeReview(attitudeId,id));
        var appraisals=bean(AnnualAppraisalService.class);var readiness=as(ALICE,id->appraisals.readiness(participant,id));
        check(readiness.ready(),"Readiness blockers: "+readiness.blockers());
        var result=new LinkedHashMap<String,Object>();result.put("periodId",periodId);result.put("participantId",participant);
        result.put("kpiAssessmentIds",assessmentIds);result.put("attitudeAssessmentId",attitudeId);
        result.put("annualKpiScore",readiness.performance().annualKpiScore());result.put("attitudeScore",readiness.performance().attitudeScore());result.put("finalScore",readiness.performance().finalScore());
        if(approve) {
            var recommendation=prefix.contains("2023")?AnnualAppraisalRecommendation.NO_RECOMMENDATION:AnnualAppraisalRecommendation.SALARY_INCREMENT;
            var draft=as(ALICE,id->appraisals.create(new AnnualAppraisalRequest(participant,null,recommendation,
                prefix+" SIMULATED UAT HISTORY ONLY. Not an official employment or salary decision. Consistent delivery and collaborative behaviour support this test recommendation."),id));
            var pending=as(ALICE,id->appraisals.submit(draft.getId(),new AnnualAppraisalActionRequest(draft.getLockVersion(),null,null),id));
            var approved=as(DAVID,id->appraisals.approve(pending.getId(),new AnnualAppraisalActionRequest(pending.getLockVersion(),null,null),id));
            check(approved.getStatus()==AppraisalStatus.APPROVED,"History approval failed");result.put("appraisalId",approved.getId());
        }
        System.out.println(prefix+" calculated scores: KPI="+result.get("annualKpiScore")+", Attitude="+result.get("attitudeScore")+", Final="+result.get("finalScore"));
        return result;
    }
    void publishCentral(KpiPlanDto plan,Long checkpoint,String email,int point,String prefix,String actual) throws Exception {
        var request=new KpiCentralResultRequest();request.setPlanVersionId(plan.getCurrentVersionId());request.setCheckpointId(checkpoint);
        request.setItems(plan.getItems().stream().map(item->{var a=new KpiCentralResultRequest.Answer();a.setKpiId(item.getId());a.setPoint(point);
            a.setActualAchievement(prefix+" "+actual);a.setComment(prefix+" Consolidated from the checkpoint management report; simulated test result.");return a;}).toList());
        var service=bean(KpiCentralResultService.class);var draft=as(email,id->service.create(request,id));
        var decision=new KpiCentralResultDecisionRequest();decision.setExpectedLockVersion(draft.getLockVersion());
        var published=as(email,id->service.publish(draft.getId(),decision,id));check(published.getStatus()==KpiCentralResultStatus.PUBLISHED,"Central result publication failed");
    }
    void verify() {
        try {
            var service=bean(AnnualAppraisalService.class);var current=as(ALICE,id->service.readiness(24L,id));
            check(current.ready(),"Bob must be genuinely ready: "+current.blockers());
            check(current.performance().annualKpiScore().compareTo(new BigDecimal("85.6000"))==0,"Unexpected current KPI score");
            check(current.performance().attitudeScore().compareTo(new BigDecimal("86.6667"))==0,"Unexpected current Attitude score");
            check(current.performance().finalScore().compareTo(new BigDecimal("86.1334"))==0,"Unexpected current score: "+current.performance().finalScore());
            check(!as(ALICE,id->service.readiness(23L,id)).ready(),"Carol must remain Waiting for Results");
            check(bean(com.tbm.careerpathlearning.repository.AnnualAppraisalRepository.class).findByParticipantId(24L).isEmpty(),"Leave Bob's current appraisal for manual UAT");
            var currentData=(Map<?,?>)manifest.get("current");Long attitudeId=((Number)currentData.get("attitudeAssessmentId")).longValue();
            var employeeResult=as(BOB,id->bean(AttitudeAssessmentService.class).get(attitudeId,id));
            check(employeeResult.getStatus()==AttitudeAssessmentStatus.REVIEWED && employeeResult.getItems().size()==3,"Completed employee Attitude result missing");
            check(employeeResult.getItems().stream().allMatch(i->i.getSelfPoint()!=null && i.getSuperiorPoint()!=null && i.getSuperiorComment()!=null),"Employee-visible submitted feedback missing");
            for(var assessment:current.performance().checkpoints()) {
                var employeeKpi=as(BOB,id->bean(KpiAssessmentService.class).get(assessment.assessmentId(),id));
                check(employeeKpi.getStatus()==KpiAssessmentStatus.REVIEWED && Boolean.TRUE.equals(employeeKpi.getSubmittedLate()) && Boolean.TRUE.equals(employeeKpi.getReviewedLate()),"Historical KPI audit must retain real overdue flags");
            }
            var history=as(ALICE,id->service.history(24L,id));
            for(int year:List.of(2023,2024)) {
                var entries=history.stream().filter(a->a.getReviewPeriodName().equals("[UAT SIMULATED] "+year+" Annual Review")).toList();
                check(entries.size()==1 && entries.get(0).getStatus()==AppraisalStatus.APPROVED,"Missing or duplicate simulated history for "+year);
                check(entries.get(0).getJustification().contains("Not an official employment or salary decision"),"Simulation label missing");
                check(entries.get(0).getSnapshot().checkpoints().size()==4,"All four historical quarterly assessments required");
                var expected=new BigDecimal(year==2023?"69.4667":"78.5000");
                check(entries.get(0).getSnapshot().finalScore().compareTo(expected)==0,"Historical score does not match expected calculation");
                UUID reviewer;try(var c=connection(false)){reviewer=actor(c,DAVID).id();}
                check(reviewer.equals(entries.get(0).getReviewedBy()) && entries.get(0).getReviewedAt()!=null && Boolean.TRUE.equals(entries.get(0).getReviewedLate()),"Normal HR approval audit missing");
            }
            System.out.println("Verified normal readiness: Bob ready / Carol waiting; two approved, labelled simulated history records.");
        } catch(Exception e) {throw new IllegalStateException("Fixture verification failed",e);}
    }
    void verifyHttp() throws Exception {
        var team=get(ALICE,"/api/annual-appraisals/team?reviewPeriodId=7",200);
        var bob=java.util.stream.StreamSupport.stream(team.spliterator(),false).filter(row->row.path("participantId").asLong()==24).findFirst().orElseThrow();
        var carol=java.util.stream.StreamSupport.stream(team.spliterator(),false).filter(row->row.path("participantId").asLong()==23).findFirst().orElseThrow();
        check(bob.path("readiness").path("ready").asBoolean() && !carol.path("readiness").path("ready").asBoolean(),"Scoped HTTP team readiness mismatch");
        var history=get(ALICE,"/api/annual-appraisals/participants/24/history",200);
        check(history.size()==2,"HTTP history must contain exactly the two simulated approvals");
        var result=get(BOB,"/api/attitude-assessments/mine?reviewPeriodId=7",200);
        check("REVIEWED".equals(result.path("status").asText()) && result.path("items").size()==3,"Employee HTTP result missing");
        for(var item:result.path("items")) check(item.hasNonNull("superiorPoint") && item.hasNonNull("superiorComment"),"Employee HTTP feedback missing");
        get(BOB,"/api/annual-appraisals/participants/24/history",403);
        System.out.println("PASS: scoped read-only HTTP checks: Alice readiness/history; Bob released Attitude feedback; employee appraisal-history denial.");
    }
    com.fasterxml.jackson.databind.JsonNode get(String email,String path,int expectedStatus)throws Exception {
        Actor actor;try(var c=connection(false)){c.setReadOnly(true);actor=actor(c,email);}
        String token=bean(TokenService.class).generateAccessToken(actor.id(),actor.permissions());
        int port=((org.springframework.boot.web.context.WebServerApplicationContext)app).getWebServer().getPort();
        var request=java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(20))
            .header("Authorization","Bearer "+token).GET().build();
        var response=java.net.http.HttpClient.newHttpClient().send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
        check(response.statusCode()==expectedStatus,"HTTP "+path+" expected "+expectedStatus+" but received "+response.statusCode());
        return expectedStatus==200?JSON.readTree(response.body()):JSON.nullNode();
    }
    KpiItemDto kpi(String name,String perspective,String kra,String target,String unit,int weight,List<String> definitions) {
        var item=new KpiItemDto();item.setName(name);item.setDescription("Disposable Phase 4 UAT example; not a genuine employee performance record.");
        item.setPerspective(perspective);item.setKra(kra);item.setTarget(target);item.setMeasurementUnit(unit);item.setWeightage(BigDecimal.valueOf(weight));
        for(int i=0;i<5;i++) item.getScoringDefinitions().put(i+1,definitions.get(i));return item;
    }
    KpiPlanRequest plan(Long period,Long department,Long participant,List<KpiItemDto> items) {
        var request=new KpiPlanRequest();request.setReviewPeriodId(period);request.setDepartmentId(department);request.setOwnerParticipantId(participant);request.setItems(items);return request;
    }
    Long companyId(Long period) {return bean(KpiPlanService.class).companyPlans().stream().filter(p->p.getReviewPeriodId().equals(period)).findFirst().orElseThrow().getId();}
    Long participant(Long period,String email) throws Exception {
        var participants=bean(com.tbm.careerpathlearning.repository.ReviewPeriodParticipantRepository.class).findAllByReviewPeriodId(period);
        UUID staff;try(var c=connection(false)){staff=actor(c,email).id();}
        return participants.stream().filter(p->p.getStaff().getId().equals(staff)).findFirst().orElseThrow().getId();
    }
    <T> T bean(Class<T> type){return app.getBean(type);}
    <T> T as(String email,Function<UUID,T> action) throws Exception {
        Actor actor;try(var c=connection(false)){c.setReadOnly(true);actor=actor(c,email);}
        var previous=SecurityContextHolder.getContext();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(actor.id(),null,actor.permissions().stream().map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(context);
        try {
            var result=action.apply(actor.id());
            // HTTP calls normally have separate persistence contexts. Reload associations
            // between fixture actions while retaining one rollback-safe transaction.
            var em=org.springframework.orm.jpa.EntityManagerFactoryUtils.getTransactionalEntityManager(bean(jakarta.persistence.EntityManagerFactory.class));
            if(em!=null) {
                if(!org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()) em.flush();
                em.clear();
            }
            return result;
        } finally {SecurityContextHolder.setContext(previous);}
    }
    Actor actor(Connection c,String email) throws Exception {
        String staff=scalar(c,"SELECT id FROM staff WHERE email=? AND account_status='ACTIVE' AND is_deleted=false",email);check(staff!=null,"Active actor missing: "+email);
        var permissions=new ArrayList<String>();try(var s=c.prepareStatement("SELECT a.name FROM staff s JOIN role_authority ra ON ra.role_id=s.role_id JOIN authority a ON a.id=ra.authority_id JOIN role r ON r.id=s.role_id WHERE s.email=? AND r.is_deleted=false ORDER BY a.name")){
            s.setString(1,email);try(var r=s.executeQuery()){while(r.next())permissions.add(r.getString(1));}}
        return new Actor(UUID.fromString(staff),permissions);
    }
    void requireAuthority(Connection c,String email,String permission)throws Exception {check(actor(c,email).permissions().contains(permission),"Existing permission missing: "+email+" / "+permission);}
    Connection connection(boolean development)throws Exception {
        var c=DriverManager.getConnection(development?env.get("DB_URL"):url,env.get("DB_USER"),env.get("DB_PASS"));
        if(!development && (!DB.equals(scalar(c,"SELECT current_database()")) || !MARKER.equals(scalar(c,"SELECT shobj_description(oid,'pg_database') FROM pg_database WHERE datname=current_database()")))){c.close();throw new IllegalStateException("UAT identity mismatch");}
        return c;
    }
    static String scalar(Connection c,String sql,Object... parameters)throws Exception {
        try(var s=c.prepareStatement(sql)){for(int i=0;i<parameters.length;i++)s.setObject(i+1,parameters[i]);try(var r=s.executeQuery()){return r.next()?r.getString(1):null;}}
    }
    Map<String,List<String>> snapshot(Connection c)throws Exception {
        var tables=new ArrayList<String>();try(var s=c.createStatement();var rows=s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")){while(rows.next())tables.add(rows.getString(1));}
        var data=new TreeMap<String,List<String>>();for(var table:tables){var hashes=new ArrayList<String>();
            try(var s=c.createStatement();var rows=s.executeQuery("SELECT md5(row_to_json(t)::text) FROM public.\""+table.replace("\"","\"\"")+"\" t ORDER BY 1")){while(rows.next())hashes.add(rows.getString(1));}data.put(table,hashes);}
        return data;
    }
    void backup()throws Exception {
        var destination=STATE.resolve("before-fixture-"+System.currentTimeMillis()+".dump").toAbsolutePath();
        var process=new ProcessBuilder("C:/Program Files/PostgreSQL/18/bin/pg_dump.exe","--host="+source.getHost(),"--port="+source.getPort(),
            "--username="+env.get("DB_USER"),"--dbname="+DB,"--format=custom","--file="+destination);
        process.environment().put("PGPASSWORD",env.get("DB_PASS"));process.inheritIO();
        check(process.start().waitFor()==0 && Files.size(destination)>0,"Backup failed");System.out.println("Full UAT backup: "+destination);
    }
    static void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
