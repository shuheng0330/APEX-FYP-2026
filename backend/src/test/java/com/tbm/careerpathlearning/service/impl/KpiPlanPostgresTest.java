package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.KpiPlanValidator;
import com.tbm.careerpathlearning.service.KpiPlanValidatorTest;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.persistence.Entity;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import java.sql.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="APEX_PHASE2_POSTGRES_TEST",matches="true")
class KpiPlanPostgresTest {
    @Test void forwardMigrationAndJpaPlanRoundTripAreTransactional() throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        var schema="apex_kpi_test_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            c.setAutoCommit(false);
            try {
                sql(c,"CREATE SCHEMA "+schema);sql(c,"SET LOCAL search_path TO "+schema);
                for(var table:List.of("org_chart","role","staff","authority","employee_level","annual_kpi_review_period",
                        "review_period_employee_level_configuration","review_period_role_configuration","review_checkpoint","review_period_participant","appraisal_record"))
                    sql(c,"CREATE TABLE "+schema+"."+table+" (LIKE public."+table+" INCLUDING ALL)");
                sql(c,"ALTER TABLE review_period_participant DROP CONSTRAINT IF EXISTS uq_participant_id_period");
                sql(c,new ClassPathResource("db/migration/annual-kpi/V31__kpi_plan_foundation.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                sql(c,"ALTER TABLE annual_kpi_review_period DROP COLUMN IF EXISTS participants_snapshotted_at");
                sql(c,new ClassPathResource("db/migration/annual-kpi/V32__review_participant_snapshot_and_company_publication.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                sql(c,new ClassPathResource("db/migration/annual-kpi/V33__department_kpi_plan_review.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                sql(c,new ClassPathResource("db/migration/annual-kpi/V34__individual_kpi_plan_review.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                sql(c,"ALTER TABLE appraisal_record DROP CONSTRAINT IF EXISTS ck_appraisal_revision_required");
                sql(c,"ALTER TABLE appraisal_record DROP COLUMN IF EXISTS revision_required");
                sql(c,new ClassPathResource("db/migration/annual-kpi/V35__require_changes_after_return.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                sql(c,new ClassPathResource("db/migration/annual-kpi/V36__individual_kpi_assistance.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                sql(c,new ClassPathResource("db/migration/annual-kpi/V37__individual_kpi_assistance_rejection.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                sql(c,new ClassPathResource("db/migration/annual-kpi/V38__individual_kpi_assistance_request_reason.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                sql(c,"ALTER TABLE review_period_participant DROP CONSTRAINT IF EXISTS uq_participant_frequency");
                sql(c,"ALTER TABLE review_checkpoint DROP CONSTRAINT IF EXISTS uq_checkpoint_frequency");
                sql(c,new ClassPathResource("db/migration/annual-kpi/V39__kpi_self_assessment.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                // Clone only inherited actors inside the rollback-only test schema; no live Role grants.
                sql(c,"INSERT INTO org_chart SELECT * FROM public.org_chart");
                sql(c,"INSERT INTO role SELECT * FROM public.role");sql(c,"INSERT INTO staff SELECT * FROM public.staff");
                sql(c,"INSERT INTO employee_level SELECT * FROM public.employee_level");
                for(var table:List.of("org_chart","role"))
                    sql(c,"ALTER TABLE "+schema+"."+table+" ALTER COLUMN id RESTART WITH "+(scalar(c,"SELECT coalesce(max(id),0) FROM "+table)+1));
                var config=new Configuration();config.setProperty("hibernate.connection.url",url);
                config.setProperty("hibernate.connection.username",env.get("DB_USER"));config.setProperty("hibernate.connection.password",env.get("DB_PASS"));
                config.setProperty("hibernate.hbm2ddl.auto","none");config.setProperty("hibernate.default_schema",schema);
                config.setProperty("hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
                var scanner=new ClassPathScanningCandidateComponentProvider(false);scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
                for(var candidate:scanner.findCandidateComponents("com.tbm.careerpathlearning.model")) config.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
                try(var sf=config.buildSessionFactory();var session=sf.withOptions().connection(c).openSession()) {
                    session.beginTransaction();
                    var factory=new JpaRepositoryFactory(session);var periods=factory.getRepository(AnnualKpiReviewPeriodRepository.class);
                    var plans=factory.getRepository(KpiPlanRepository.class);
                    var period=new AnnualKpiReviewPeriod();period.setName("KPI isolated draft");periods.saveAndFlush(period);
                    UUID actor=session.createQuery("select s.id from Staff s",UUID.class).setMaxResults(1).getSingleResult();
                    var service=new KpiPlanServiceImpl(plans,periods,Mappers.getMapper(KpiPlanMapper.class),new KpiPlanValidator(),Clock.systemUTC(),
                        new com.tbm.careerpathlearning.service.KpiAssignmentService(factory.getRepository(ReviewPeriodParticipantRepository.class),factory.getRepository(EmployeeKpiAssignmentRepository.class),Clock.systemUTC()),
                        factory.getRepository(StaffRepository.class),factory.getRepository(ReviewPeriodParticipantRepository.class),
                        factory.getRepository(OrgChartRepository.class),
                        new com.tbm.careerpathlearning.service.PerformanceDepartmentResolver(factory.getRepository(OrgChartRepository.class),factory.getRepository(ParentChildNodeRepository.class)),
                        factory.getRepository(IndividualKpiAssistanceAuthorizationRepository.class),
                        Mappers.getMapper(com.tbm.careerpathlearning.mapper.KpiAssistanceMapper.class));
                    var request=new KpiPlanRequest();request.setReviewPeriodId(period.getId());request.setItems(List.of(com.tbm.careerpathlearning.service.KpiPlanValidatorTest.item("Sales","100")));
                    var result=service.createCompany(request,actor);session.clear();
                    var loaded=service.companyPlan(result.getId());assertEquals(5,loaded.getItems().get(0).getScoringDefinitions().size());
                    request.setItems(loaded.getItems());request.getItems().get(0).setName("Revised Draft");
                    service.updateCompany(result.getId(),request,actor);session.clear();assertEquals("Revised Draft",service.companyPlan(result.getId()).getItems().get(0).getName());
                    request.setItems(List.of());service.updateCompany(result.getId(),request,actor);session.clear();assertTrue(service.companyPlan(result.getId()).getItems().isEmpty());
                    request.setItems(List.of(com.tbm.careerpathlearning.service.KpiPlanValidatorTest.item("Sales","60"),
                        com.tbm.careerpathlearning.service.KpiPlanValidatorTest.item("Customers","40")));
                    service.updateCompany(result.getId(),request,actor);
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.publishCompany(result.getId(),actor));
                    var savedPeriod=periods.findById(period.getId()).orElseThrow();
                    Mappers.getMapper(com.tbm.careerpathlearning.mapper.AnnualKpiReviewPeriodMapper.class)
                        .updateConfiguration(AnnualKpiReviewPeriodServiceImplTest.validRequest(),savedPeriod);
                    savedPeriod.setStatus(AnnualKpiReviewPeriodStatus.OPEN);savedPeriod.setParticipantsSnapshottedAt(OffsetDateTime.now());periods.saveAndFlush(savedPeriod);
                    var department=new OrgChart();department.setName("Company cascade test department");department.setType(OrgChartType.D);
                    department.setCreatedAt(OffsetDateTime.now());department.setUpdatedAt(department.getCreatedAt());session.persist(department);
                    var role=new Role();role.setName("Company cascade test role");role.setOrgChart(department);
                    role.setCreatedAt(OffsetDateTime.now());role.setUpdatedAt(role.getCreatedAt());role.setEmployeeLevel(session.find(EmployeeLevel.class,4L));session.persist(role);
                    var weights=new ReviewPeriodEmployeeLevelConfiguration();weights.setReviewPeriod(savedPeriod);weights.setEmployeeLevel(role.getEmployeeLevel());
                    weights.setCompanyKpiWeight(new java.math.BigDecimal("15"));weights.setDepartmentKpiWeight(new java.math.BigDecimal("25"));
                    weights.setIndividualKpiWeight(new java.math.BigDecimal("60"));session.persist(weights);session.flush();
                    var roleConfiguration=new ReviewPeriodRoleConfiguration();roleConfiguration.setReviewPeriod(savedPeriod);roleConfiguration.setRole(role);
                    roleConfiguration.setEmployeeLevelConfiguration(weights);roleConfiguration.setEmployeeLevelConfigurationId(weights.getId());
                    roleConfiguration.setReviewFrequency(ReviewFrequency.MONTHLY);session.persist(roleConfiguration);
                    var participants=factory.getRepository(ReviewPeriodParticipantRepository.class);
                    for(int i=0;i<2;i++) {
                        var employee=new Staff();employee.setId(UUID.randomUUID());employee.setName("Company cascade employee "+i);
                        employee.setEmail(employee.getId()+"@example.test");employee.setRole(role);employee.setAccountStatus(StaffAccountStatus.ACTIVE);
                        employee.setCreatedAt(OffsetDateTime.now());employee.setUpdatedAt(employee.getCreatedAt());session.persist(employee);
                        participants.saveAndFlush(new com.tbm.careerpathlearning.service.ReviewPeriodParticipantFactory().snapshot(savedPeriod,employee,roleConfiguration,department));
                    }
                    var rosterIds=participants.findAllByReviewPeriodId(savedPeriod.getId()).stream().map(ReviewPeriodParticipant::getId).toList();
                    var published=service.publishCompany(result.getId(),actor);assertEquals(KpiPlanStatus.PUBLISHED,published.getStatus());
                    assertEquals(4L,scalar(c,"SELECT count(*) FROM employee_kpi_assignment"));
                    assertEquals(rosterIds,participants.findAllByReviewPeriodId(savedPeriod.getId()).stream().map(ReviewPeriodParticipant::getId).toList());
                    assertEquals(0,new java.math.BigDecimal("100").compareTo(published.getTotalWeightage()));
                    reject(c,"23505","INSERT INTO employee_kpi_assignment(kpi_id,participant_id,review_period_id,assigned_at) "
                        +"SELECT kpi_id,participant_id,review_period_id,assigned_at FROM employee_kpi_assignment");
                    var other=new AnnualKpiReviewPeriod();other.setName("Cross-period assignment guard");periods.saveAndFlush(other);
                    reject(c,"23503","UPDATE employee_kpi_assignment SET review_period_id="+other.getId());
                    reject(c,"23514","UPDATE kpi_plan SET published_at=NULL WHERE id="+published.getId());
                    assertNotNull(published.getPublishedAt());assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.publishCompany(result.getId(),actor));
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.updateCompany(result.getId(),request,actor));
                    // Assign an HOD role only in this rollback-only fixture; production grants remain unchanged.
                    var hod=session.find(Staff.class,actor);hod.setRole(role);hod.setDeleted(false);hod.setAccountStatus(StaffAccountStatus.ACTIVE);
                    session.flush();
                    var departmentRequest=new KpiPlanRequest();departmentRequest.setReviewPeriodId(savedPeriod.getId());
                    departmentRequest.setDepartmentId(department.getId());departmentRequest.setItems(List.of());
                    var departmentDraft=service.createDepartment(departmentRequest,actor);
                    assertEquals(KpiPlanStatus.DRAFT,departmentDraft.getStatus());
                    assertTrue(service.departmentPlans(actor).stream().allMatch(p->p.getDepartmentId().equals(department.getId())));
                    departmentRequest.setItems(List.of(KpiPlanValidatorTest.item("Department sales","60"),KpiPlanValidatorTest.item("Department service","40")));
                    var updated=service.updateDepartment(departmentDraft.getId(),departmentRequest,actor);
                    assertEquals(2,updated.getItems().size());
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.createDepartment(departmentRequest,actor));
                    reject(c,"23514","UPDATE kpi_plan SET status='PENDING_APPROVAL' WHERE id="+departmentDraft.getId());
                    var pending=service.submitDepartment(departmentDraft.getId(),actor);session.clear();
                    assertEquals(KpiPlanStatus.PENDING_APPROVAL,service.departmentPlan(pending.getId(),actor).getStatus());
                    assertEquals(hod.getName(),service.departmentPlan(pending.getId(),actor).getSubmittedByName());
                    assertNotNull(pending.getSubmittedAt());assertEquals(actor,pending.getSubmittedBy());
                    reject(c,"23514","UPDATE kpi_plan SET status='RETURNED',reviewed_at=now(),reviewed_by=created_by,reviewed_late=false WHERE id="+pending.getId());
                    var reason=new KpiPlanReturnRequest();reason.setReason("Clarify department target");
                    var returned=service.returnDepartment(pending.getId(),reason,actor);session.clear();
                    assertEquals("Clarify department target",service.departmentPlan(returned.getId(),actor).getReturnReason());
                    assertEquals(4L,scalar(c,"SELECT count(*) FROM employee_kpi_assignment"));
                    assertTrue(service.departmentPlan(returned.getId(),actor).isRevisionRequired());
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.submitDepartment(returned.getId(),actor));
                    departmentRequest.setItems(service.departmentPlan(returned.getId(),actor).getItems());
                    service.updateDepartment(returned.getId(),departmentRequest,actor);session.flush();session.clear();
                    assertTrue(service.departmentPlan(returned.getId(),actor).isRevisionRequired());
                    departmentRequest.getItems().get(0).setTarget("Clarified department target");
                    service.updateDepartment(returned.getId(),departmentRequest,actor);session.flush();session.clear();
                    assertFalse(service.departmentPlan(returned.getId(),actor).isRevisionRequired());
                    var resubmitted=service.submitDepartment(returned.getId(),actor);assertNull(resubmitted.getReturnReason());
                    var approved=service.approveDepartment(returned.getId(),actor);session.clear();
                    assertEquals(KpiPlanStatus.APPROVED,service.departmentPlan(approved.getId(),actor).getStatus());
                    assertEquals(8L,scalar(c,"SELECT count(*) FROM employee_kpi_assignment"));
                    assertEquals(4L,scalar(c,"SELECT count(*) FROM employee_kpi_assignment a JOIN kpi k ON k.id=a.kpi_id "
                        +"JOIN review_period_participant p ON p.id=a.participant_id WHERE k.plan_id="+approved.getId()+" AND p.department_id="+department.getId()));
                    reject(c,"23514","UPDATE kpi_plan SET reviewed_by=NULL WHERE id="+approved.getId());
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.approveDepartment(approved.getId(),actor));
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.updateDepartment(approved.getId(),departmentRequest,actor));
                    reject(c,"23505","INSERT INTO kpi_plan(review_period_id,level,department_id,status,created_at,updated_at,created_by,updated_by) "
                        +"SELECT review_period_id,level,department_id,'DRAFT',created_at,updated_at,created_by,updated_by FROM kpi_plan WHERE id="+approved.getId());
                    var owner=participants.findAllByReviewPeriodId(savedPeriod.getId()).get(0).getStaff();
                    owner.setManager(hod);session.flush();
                    var individualRequest=new KpiPlanRequest();individualRequest.setReviewPeriodId(savedPeriod.getId());
                    individualRequest.setItems(List.of(KpiPlanValidatorTest.item("Individual sales","100")));
                    var individualDraft=service.createIndividual(individualRequest,owner.getId());
                    assertEquals(KpiPlanStatus.DRAFT,individualDraft.getStatus());
                    assertEquals(1,service.myIndividualPlans(owner.getId()).size());
                    var individualPending=service.submitIndividual(individualDraft.getId(),owner.getId());
                    assertEquals(hod.getId(),individualPending.getSubmittedToSuperiorId());
                    assertEquals(1,service.pendingIndividualPlans(hod.getId()).size());
                    assertEquals(1,service.individualReviewPlans(hod.getId()).size());
                    var individualReturn=new KpiPlanReturnRequest();individualReturn.setReason("Clarify the sales target");
                    assertEquals(KpiPlanStatus.RETURNED,service.returnIndividual(individualDraft.getId(),individualReturn,hod.getId()).getStatus());
                    assertEquals("Clarify the sales target",service.individualPlan(individualDraft.getId(),owner.getId()).getReturnReason());
                    session.flush();session.clear();
                    assertTrue(service.individualPlan(individualDraft.getId(),owner.getId()).isRevisionRequired());
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.submitIndividual(individualDraft.getId(),owner.getId()));
                    individualRequest.setItems(service.individualPlan(individualDraft.getId(),owner.getId()).getItems());
                    individualRequest.getItems().get(0).setTarget("Clarified individual target");
                    service.updateIndividual(individualDraft.getId(),individualRequest,owner.getId());session.flush();session.clear();
                    assertFalse(service.individualPlan(individualDraft.getId(),owner.getId()).isRevisionRequired());
                    assertEquals(KpiPlanStatus.PENDING_APPROVAL,service.submitIndividual(individualDraft.getId(),owner.getId()).getStatus());
                    var individualApproved=service.approveIndividual(individualDraft.getId(),hod.getId());
                    assertEquals(KpiPlanStatus.APPROVED,individualApproved.getStatus());
                    assertEquals(1L,scalar(c,"SELECT count(*) FROM employee_kpi_assignment a JOIN kpi k ON k.id=a.kpi_id "
                        +"WHERE k.plan_id="+individualDraft.getId()+" AND a.participant_id="
                        +participants.findByReviewPeriodIdAndStaffId(savedPeriod.getId(),owner.getId()).orElseThrow().getId()));
                    assertEquals(3,service.myAssignedPlans(savedPeriod.getId(),owner.getId()).size());
                    assertEquals(KpiPlanStatus.APPROVED,service.individualReviewPlans(hod.getId()).get(0).getStatus());
                    var assessmentClock=Clock.fixed(Instant.parse("2027-02-06T04:00:00Z"),ZoneId.of("Asia/Kuala_Lumpur"));
                    var checkpoint=new ReviewCheckpoint();checkpoint.setReviewPeriod(session.find(AnnualKpiReviewPeriod.class,savedPeriod.getId()));
                    checkpoint.setReviewFrequency(ReviewFrequency.MONTHLY);checkpoint.setSequenceNumber(1);
                    checkpoint.setStartDate(LocalDate.of(2027,1,1));checkpoint.setEndDate(LocalDate.of(2027,1,31));
                    checkpoint.setSelfAssessmentDeadline(LocalDate.of(2027,2,5));checkpoint.setSuperiorAssessmentDeadline(LocalDate.of(2027,2,10));
                    session.persist(checkpoint);session.flush();
                    // Assessment HTTP requests begin after the approval transaction, with a fresh persistence context.
                    session.clear();
                    var assessmentRepository=factory.getRepository(KpiAssessmentRepository.class);
                    var assessmentService=new KpiAssessmentServiceImpl(assessmentRepository,
                        factory.getRepository(KpiAssessmentItemRepository.class),factory.getRepository(KpiAssessmentEvidenceRepository.class),
                        participants,factory.getRepository(ReviewCheckpointRepository.class),factory.getRepository(EmployeeKpiAssignmentRepository.class),
                        periods,factory.getRepository(StaffRepository.class),Mappers.getMapper(KpiPlanMapper.class),
                        Mappers.getMapper(com.tbm.careerpathlearning.mapper.KpiAssessmentMapper.class),
                        org.mockito.Mockito.mock(com.tbm.careerpathlearning.service.KpiAssessmentEvidenceStorage.class),
                        org.mockito.Mockito.mock(com.tbm.careerpathlearning.service.EmailService.class),new com.tbm.careerpathlearning.service.KpiCheckpointScoreCalculator(),assessmentClock);
                    assertNull(assessmentService.mine(checkpoint.getId(),owner.getId()).getId());
                    assertEquals(0L,scalar(c,"SELECT count(*) FROM kpi_assessment"));
                    var assessmentRequest=new KpiAssessmentRequest();assessmentRequest.setCheckpointId(checkpoint.getId());
                    var assessmentDraft=assessmentService.create(assessmentRequest,owner.getId());session.clear();
                    assertEquals(5,assessmentService.get(assessmentDraft.getId(),owner.getId()).getItems().size());
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,
                        ()->assessmentService.submit(assessmentDraft.getId(),owner.getId()));
                    session.clear();
                    assertEquals(KpiAssessmentStatus.DRAFT,assessmentService.get(assessmentDraft.getId(),owner.getId()).getStatus());
                    assessmentRequest.setItems(assessmentService.get(assessmentDraft.getId(),owner.getId()).getItems().stream().map(i->{
                        var answer=new KpiAssessmentRequest.Answer();answer.setAssignmentId(i.getAssignmentId());
                        answer.setSelfPoint(4);answer.setSelfComment("Observed performance for this checkpoint");return answer;
                    }).toList());
                    assessmentService.update(assessmentDraft.getId(),assessmentRequest,owner.getId());session.clear();
                    var submittedAssessment=assessmentService.submit(assessmentDraft.getId(),owner.getId());session.clear();
                    var reloadedAssessment=assessmentService.get(submittedAssessment.getId(),owner.getId());
                    assertEquals(KpiAssessmentStatus.PENDING_REVIEW,reloadedAssessment.getStatus());
                    assertTrue(reloadedAssessment.getSubmittedLate());assertEquals(hod.getId(),reloadedAssessment.getSubmittedToSuperiorId());
                    assertNull(reloadedAssessment.getCheckpointScore());
                    assertEquals(5,reloadedAssessment.getItems().size());
                    assertTrue(reloadedAssessment.getItems().stream().allMatch(i->i.getSelfPoint()==4 && i.getSuperiorPoint()==null));
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,
                        ()->assessmentService.update(assessmentDraft.getId(),assessmentRequest,owner.getId()));
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,
                        ()->assessmentService.submit(assessmentDraft.getId(),owner.getId()));
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(hod.getId(),null,
                            List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("CAN_REVIEW_KPI_ASSESSMENT"))));
                    try {
                        var queue=assessmentService.reviews(savedPeriod.getId(),KpiAssessmentStatus.PENDING_REVIEW,hod.getId());
                        assertEquals(1,queue.size());assertEquals(submittedAssessment.getId(),queue.get(0).getId());
                        assertTrue(queue.get(0).isCanReview());
                        var superiorRequest=new KpiSuperiorAssessmentRequest();
                        superiorRequest.setItems(reloadedAssessment.getItems().stream().map(i->{
                            var answer=new KpiSuperiorAssessmentRequest.Answer();answer.setItemId(i.getId());
                            answer.setSuperiorPoint(4);answer.setSuperiorComment("Reviewed evidence");return answer;
                        }).toList());
                        assessmentService.saveSuperiorDraft(submittedAssessment.getId(),superiorRequest,hod.getId());session.clear();
                        assertEquals(KpiAssessmentStatus.PENDING_REVIEW,assessmentService.get(submittedAssessment.getId(),hod.getId()).getStatus());
                        assertTrue(assessmentService.get(submittedAssessment.getId(),hod.getId()).getItems().stream().allMatch(i->i.getSuperiorPoint()==4));
                        assertTrue(assessmentService.get(submittedAssessment.getId(),owner.getId()).getItems().stream().allMatch(i->i.getSuperiorPoint()==null));
                        var reviewSavepoint=c.setSavepoint();
                        assessmentService.completeReview(submittedAssessment.getId(),hod.getId());session.flush();
                        c.rollback(reviewSavepoint);c.releaseSavepoint(reviewSavepoint);session.clear();
                        assertEquals(KpiAssessmentStatus.PENDING_REVIEW,assessmentService.get(submittedAssessment.getId(),hod.getId()).getStatus());
                        assertNull(assessmentService.get(submittedAssessment.getId(),hod.getId()).getCheckpointScore());
                        assessmentService.completeReview(submittedAssessment.getId(),hod.getId());session.clear();
                        var official=assessmentService.get(submittedAssessment.getId(),owner.getId());
                        assertEquals(KpiAssessmentStatus.REVIEWED,official.getStatus());
                        assertEquals(new java.math.BigDecimal("80.0000"),official.getCheckpointScore());
                        assertEquals(hod.getId(),official.getReviewedBy());assertFalse(official.getReviewedLate());
                        assertTrue(official.getItems().stream().allMatch(i->i.getSuperiorPoint()==4 && i.getSelfPoint()==4));
                        assertEquals(1,assessmentService.reviews(savedPeriod.getId(),KpiAssessmentStatus.REVIEWED,hod.getId()).size());
                        assertTrue(assessmentService.reviews(savedPeriod.getId(),KpiAssessmentStatus.PENDING_REVIEW,hod.getId()).isEmpty());
                        assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,
                            ()->assessmentService.completeReview(submittedAssessment.getId(),hod.getId()));
                    } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
                    session.find(Staff.class,owner.getId()).setManager(null);session.flush();
                    assertTrue(service.individualReviewPlans(hod.getId()).isEmpty());
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,
                        ()->service.updateIndividual(individualDraft.getId(),individualRequest,owner.getId()));
                    // Assistance is a separate pre-creation HR decision, then direct Superior confirmation.
                    var assistedParticipant=participants.findAllByReviewPeriodId(savedPeriod.getId()).stream()
                        .filter(p->!p.getStaff().getId().equals(owner.getId())).findFirst().orElseThrow();
                    assistedParticipant.getStaff().setManager(session.find(Staff.class,actor));session.flush();
                    assertEquals(1,service.assistanceEmployees(actor).size());
                    var assistanceRequest=new KpiAssistanceRequest();assistanceRequest.setOwnerParticipantId(assistedParticipant.getId());
                    assistanceRequest.setRequestReason("Needs assistance preparing KPIs");
                    var rejected=service.requestAssistance(assistanceRequest,actor);session.flush();session.clear();
                    var rejection=new KpiPlanReturnRequest();rejection.setReason("Please clarify assistance");
                    service.rejectAssistance(rejected.getId(),rejection,actor);session.flush();session.clear();
                    assertEquals("Please clarify assistance",service.assistanceCase(rejected.getId(),actor).getRejectionReason());
                    var requested=service.requestAssistance(assistanceRequest,actor);session.flush();session.clear();
                    assertEquals("Needs assistance preparing KPIs",service.assistanceCase(requested.getId(),actor).getRequestReason());
                    assertNotEquals(rejected.getId(),requested.getId());
                    assertEquals(KpiAssistanceStatus.REQUESTED,service.assistanceCase(requested.getId(),actor).getStatus());
                    var assistedItems=new AssistedIndividualKpiPlanRequest();assistedItems.setItems(List.of(KpiPlanValidatorTest.item("Assisted customers","100")));
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,
                        ()->service.createAssistedIndividual(requested.getId(),assistedItems,actor));
                    service.authorizeAssistance(requested.getId(),actor);session.flush();session.clear();
                    var assistedDraft=service.createAssistedIndividual(requested.getId(),assistedItems,actor);session.flush();session.clear();
                    assertEquals(KpiPlanStatus.DRAFT,service.assistedIndividualPlan(requested.getId(),actor).getStatus());
                    assertEquals(assistedParticipant.getId(),assistedDraft.getOwnerParticipantId());
                    var beforeAssignments=scalar(c,"SELECT count(*) FROM employee_kpi_assignment");
                    reject(c,"23514","UPDATE kpi_plan SET status='PENDING_APPROVAL' WHERE id="+assistedDraft.getId());
                    reject(c,"23503","UPDATE kpi_plan SET created_by='"+owner.getId()+"' WHERE id="+assistedDraft.getId());
                    var consentRepository=factory.getRepository(IndividualKpiAssistanceAuthorizationRepository.class);
                    // A failure in the cascade must roll back both the confirmation and consumed consent.
                    var confirmationSavepoint=c.setSavepoint();
                    var failingService=new KpiPlanServiceImpl(plans,periods,Mappers.getMapper(KpiPlanMapper.class),new KpiPlanValidator(),Clock.systemUTC(),
                        new com.tbm.careerpathlearning.service.KpiAssignmentService(participants,factory.getRepository(EmployeeKpiAssignmentRepository.class),Clock.systemUTC()) {
                            @Override public void cascade(KpiPlan ignored) {throw new IllegalStateException("fixture cascade failure");}
                        },factory.getRepository(StaffRepository.class),participants,factory.getRepository(OrgChartRepository.class),
                        new com.tbm.careerpathlearning.service.PerformanceDepartmentResolver(factory.getRepository(OrgChartRepository.class),factory.getRepository(ParentChildNodeRepository.class)),
                        consentRepository,Mappers.getMapper(com.tbm.careerpathlearning.mapper.KpiAssistanceMapper.class));
                    assertThrows(IllegalStateException.class,()->failingService.confirmAssistedIndividual(requested.getId(),actor));
                    c.rollback(confirmationSavepoint);c.releaseSavepoint(confirmationSavepoint);session.clear();
                    assertEquals(KpiPlanStatus.DRAFT,service.assistedIndividualPlan(requested.getId(),actor).getStatus());
                    assertEquals(KpiAssistanceStatus.AUTHORIZED,service.assistanceCase(requested.getId(),actor).getStatus());
                    var confirmed=service.confirmAssistedIndividual(requested.getId(),actor);session.flush();session.clear();
                    assertEquals(KpiPlanStatus.APPROVED,confirmed.getStatus());assertNull(confirmed.getSubmittedAt());
                    assertEquals(KpiAssistanceStatus.CONSUMED,service.assistanceCase(requested.getId(),actor).getStatus());
                    assertEquals(beforeAssignments+1,scalar(c,"SELECT count(*) FROM employee_kpi_assignment"));
                    assertEquals(1L,scalar(c,"SELECT count(*) FROM employee_kpi_assignment a JOIN kpi k ON k.id=a.kpi_id WHERE k.plan_id="
                        +confirmed.getId()+" AND a.participant_id="+assistedParticipant.getId()));
                    assertThrows(com.tbm.careerpathlearning.exception.BadRequestException.class,()->service.confirmAssistedIndividual(requested.getId(),actor));
                    assertEquals(0,service.assistanceEmployees(actor).size());
                    session.getTransaction().rollback();
                }
            } finally {c.rollback();}
        }
    }
    @Test void sharedTransactionLockSerializesCompetingReviewActions() throws Exception {
        var env=Dotenv.configure().directory(".").load();var url=env.get("DB_URL");
        assertTrue(List.of("localhost","127.0.0.1","::1").contains(URI.create(url.substring(5)).getHost()));
        try(var first=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"));
            var second=DriverManager.getConnection(url,env.get("DB_USER"),env.get("DB_PASS"))) {
            first.setAutoCommit(false);second.setAutoCommit(false);
            try {
                sql(first,"SET LOCAL lock_timeout='3s'");
                sql(first,"SELECT 1 FROM pg_advisory_xact_lock(20261006,1)");
                sql(second,"SET LOCAL lock_timeout='300ms'");
                assertEquals("55P03",assertThrows(SQLException.class,()->sql(second,"SELECT 1 FROM pg_advisory_xact_lock(20261006,1)")).getSQLState());
                second.rollback();first.rollback();
                assertDoesNotThrow(()->sql(second,"SELECT 1 FROM pg_advisory_xact_lock(20261006,1)"));
            } finally {first.rollback();second.rollback();}
        }
    }
    private void sql(Connection c,String sql)throws SQLException {try(var s=c.createStatement()){s.execute(sql);}}
    private long scalar(Connection c,String sql)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
    private void reject(Connection c,String state,String sql)throws SQLException {
        var savepoint=c.setSavepoint();
        try {assertEquals(state,assertThrows(SQLException.class,()->sql(c,sql)).getSQLState());}
        finally {c.rollback(savepoint);c.releaseSavepoint(savepoint);}
    }
}
