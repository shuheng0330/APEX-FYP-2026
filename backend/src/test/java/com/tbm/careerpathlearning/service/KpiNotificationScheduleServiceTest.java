package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.impl.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.*;
import java.util.*;
import static com.tbm.careerpathlearning.enums.AuthorityName.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KpiNotificationScheduleServiceTest {
    final AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    final ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    final ReviewCheckpointRepository checkpoints=mock(ReviewCheckpointRepository.class);
    final KpiAssessmentRepository assessments=mock(KpiAssessmentRepository.class);
    final AttitudeAssessmentRepository attitudes=mock(AttitudeAssessmentRepository.class);
    final AnnualAppraisalRepository appraisals=mock(AnnualAppraisalRepository.class);
    final KpiPlanRepository plans=mock(KpiPlanRepository.class);
    final KpiPlanVersionRepository versions=mock(KpiPlanVersionRepository.class);
    final KpiCentralResultRepository results=mock(KpiCentralResultRepository.class);
    final CompanyKpiResultDelegateRepository delegates=mock(CompanyKpiResultDelegateRepository.class);
    final IndividualKpiAssistanceAuthorizationRepository assistance=mock(IndividualKpiAssistanceAuthorizationRepository.class);
    final KpiAssessmentServiceImpl kpiAvailability=mock(KpiAssessmentServiceImpl.class);
    final AnnualAppraisalReadinessService readiness=mock(AnnualAppraisalReadinessService.class);
    final StaffRepository staff=mock(StaffRepository.class);
    final RoleAuthorityRepository authorities=mock(RoleAuthorityRepository.class);
    final PerformanceDepartmentResolver departments=mock(PerformanceDepartmentResolver.class);
    final NotificationService notifications=mock(NotificationService.class);
    final KpiWorkflowNotifications delivery=new KpiWorkflowNotifications(notifications,staff,authorities,participants,appraisals,readiness,departments);
    final List<Staff> accounts=new ArrayList<>();
    final Map<Long,Set<AuthorityName>> grants=new HashMap<>();
    final Map<Long,List<KpiAssessment>> savedKpis=new HashMap<>();
    final Map<Long,AttitudeAssessment> savedAttitudes=new HashMap<>();
    final Map<Long,AnnualAppraisal> savedAppraisals=new HashMap<>();
    final List<ReviewPeriodParticipant> roster=new ArrayList<>();
    final List<KpiPlan> periodPlans=new ArrayList<>();
    final List<Notice> notices=new ArrayList<>();
    final Set<String> keys=new HashSet<>();
    record Notice(String key,UUID recipient,NotificationType type,String body,String link) {}
    KpiNotificationScheduleService service;
    Staff bob,alice,hr,md,entrant,outsider;
    OrgChart department;
    AnnualKpiReviewPeriod period;
    ReviewPeriodParticipant participant;
    ReviewCheckpoint april,may,june,quarter;
    KpiPlanVersion company,departmental;

    @BeforeEach void setup() {
        bob=account("Bob",ROLE_USER);alice=account("Alice",ROLE_USER,CAN_REVIEW_KPI_ASSESSMENT,CAN_REVIEW_ATTITUDE_EVALUATION,CAN_REVIEW_INDIVIDUAL_KPI,CAN_MANAGE_DEPARTMENT_KPI,CAN_SUBMIT_ANNUAL_APPRAISAL);
        hr=account("HR",ROLE_USER,CAN_REVIEW_ANNUAL_APPRAISAL,CAN_APPROVE_DEPARTMENT_KPI);
        md=account("MD",ROLE_USER,CAN_MANAGE_COMPANY_KPI);entrant=account("Entrant",ROLE_USER,CAN_ENTER_COMPANY_KPI_RESULTS);
        outsider=account("Other HOD",ROLE_USER,CAN_MANAGE_DEPARTMENT_KPI);
        bob.setManager(alice);department=new OrgChart();department.setId(10L);department.setName("IT");
        when(departments.resolve(alice.getRole())).thenReturn(department);
        var otherDepartment=new OrgChart();otherDepartment.setId(12L);when(departments.resolve(outsider.getRole())).thenReturn(otherDepartment);
        when(staff.findAllActiveWithRole()).thenAnswer(i->accounts);
        when(staff.findById(any())).thenAnswer(i->accounts.stream().filter(s->s.getId().equals(i.getArgument(0))).findFirst());
        when(authorities.existsByRoleIdAndAuthorityName(anyLong(),any())).thenAnswer(i->grants.getOrDefault(i.getArgument(0),Set.of()).contains(i.getArgument(1)));
        when(notifications.notifyInAppOnce(anyString(),any(),any(),anyString(),anyString(),anyString())).thenAnswer(i->{
            if(!keys.add(i.getArgument(0))) return false;
            notices.add(new Notice(i.getArgument(0),i.getArgument(1),i.getArgument(2),i.getArgument(4),i.getArgument(5)));return true;
        });
        period=new AnnualKpiReviewPeriod();period.setId(1L);period.setName("Q2 2027 Annual Review");period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        period.setStartDate(LocalDate.of(2027,1,1));period.setEndDate(LocalDate.of(2027,6,30));period.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);
        period.setFrequencyPolicy(KpiFrequencyPolicy.DEPARTMENT_BASED);period.setParticipantsSnapshottedAt(OffsetDateTime.parse("2027-01-01T09:00:00+08:00"));period.setOpenedAt(period.getParticipantsSnapshottedAt());
        period.setKpiSetupDeadline(LocalDate.of(2027,1,15));period.setAttitudeSelfAssessmentStartDate(LocalDate.of(2027,7,1));
        period.setAttitudeSelfAssessmentDeadline(LocalDate.of(2027,7,7));period.setSuperiorAttitudeEvaluationDeadline(LocalDate.of(2027,7,14));
        period.setAppraisalRecommendationDeadline(LocalDate.of(2027,7,21));period.setHrFinalisationDeadline(LocalDate.of(2027,7,28));
        var config=new AttitudeConfiguration();config.setId(30L);config.setStatus(AttitudeConfigurationStatus.PUBLISHED);
        var criterion=new AttitudeCriterion();criterion.setId(31L);criterion.setActive(true);criterion.setCriterionType(AttitudeCriterionType.SHARED_CORE_VALUE);config.getCriteria().add(criterion);
        period.setAttitudeConfiguration(config);
        participant=participant(7L,bob);roster.add(participant);
        when(periods.findAllByStatus(AnnualKpiReviewPeriodStatus.OPEN)).thenAnswer(i->List.of(period));
        when(participants.findAllByReviewPeriodId(1L)).thenAnswer(i->roster);
        april=checkpoint(2L,ReviewFrequency.MONTHLY,"2027-04-01","2027-04-30");may=checkpoint(3L,ReviewFrequency.MONTHLY,"2027-05-01","2027-05-31");
        june=checkpoint(4L,ReviewFrequency.MONTHLY,"2027-06-01","2027-06-30");quarter=checkpoint(5L,ReviewFrequency.QUARTERLY,"2027-04-01","2027-06-30");
        when(checkpoints.findAllByReviewPeriodIdOrderByReviewFrequencyAscSequenceNumberAsc(1L)).thenReturn(List.of(april,may,june,quarter));
        when(assessments.findAllByParticipantId(anyLong())).thenAnswer(i->savedKpis.getOrDefault(i.getArgument(0),List.of()));
        when(attitudes.findByParticipantId(anyLong())).thenAnswer(i->Optional.ofNullable(savedAttitudes.get(i.getArgument(0))));
        when(appraisals.findByParticipantId(anyLong())).thenAnswer(i->Optional.ofNullable(savedAppraisals.get(i.getArgument(0))));
        when(kpiAvailability.actionableSelfAssessmentForNotification(any(),any(),any())).thenReturn(true);
        company=version(20L,KpiLevel.COMPANY);departmental=version(21L,KpiLevel.DEPARTMENT);
        var individual=new KpiPlan();individual.setId(22L);individual.setLevel(KpiLevel.INDIVIDUAL);individual.setOwnerParticipantId(7L);individual.setStatus(KpiPlanStatus.APPROVED);individual.setReviewPeriod(period);
        periodPlans.addAll(List.of(company.getPlan(),departmental.getPlan(),individual));
        when(plans.findAllByReviewPeriodId(1L)).thenAnswer(i->periodPlans);
        when(versions.findAllByReviewPeriodIdOrderByVersionNumberDescIdDesc(1L)).thenReturn(List.of(company,departmental));
        when(results.eligibleParticipants(anyLong(),anyLong())).thenAnswer(i->i.getArgument(0).equals(20L)?(i.getArgument(1).equals(5L)?0L:1L):(i.getArgument(1).equals(5L)?1L:0L));
        when(delegates.existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(1L,entrant.getId())).thenReturn(true);
        date("2027-07-01");
    }

    @Test void openingNoticesUseRecordedQuarterlyAndMonthlySourcesAndOnlyAuthorisedEntrants() {
        service.scanToday();service.scanToday();
        assertEquals(5,notices.size());
        assertEquals(2,notices.stream().filter(n->n.recipient().equals(bob.getId())).count());
        var central=notices.stream().filter(n->n.link().contains("kpi-results")).toList();
        assertEquals(Set.of(md.getId(),entrant.getId(),alice.getId()),new HashSet<>(central.stream().map(Notice::recipient).toList()));
        assertTrue(central.stream().filter(n->n.recipient().equals(md.getId())).allMatch(n->n.link().contains("checkpointId=4")));
        assertTrue(notices.stream().filter(n->n.recipient().equals(bob.getId()) && n.link().contains("tab=kpi")).allMatch(n->n.link().contains("checkpointId=5")));
        assertTrue(notices.stream().noneMatch(n->n.link().contains("checkpointId=2") || n.link().contains("checkpointId=3")));
    }

    @ParameterizedTest @CsvSource({"2027-07-03,0","2027-07-04,2","2027-07-07,0","2027-07-08,2","2027-07-09,0"})
    void remindersOnlyAppearThreeDaysBeforeOrOnFirstOverdueDay(String day,int count) {
        date(day);service.scanToday();service.scanToday();
        assertEquals(count,notices.stream().filter(n->n.type()==NotificationType.PERFORMANCE_REMINDER && n.recipient().equals(bob.getId())).count());
        if(day.equals("2027-07-08")) assertTrue(notices.stream().allMatch(n->n.body().contains("You can still complete")));
    }

    @Test void deploymentDoesNotBackfillEarlierAvailabilityDays() {
        date("2027-07-02");service.scanToday();assertTrue(notices.isEmpty());
    }

    @Test void newlyOpenedOrEnrolledPeriodUsesItsActualOpeningDate() {
        date("2027-07-02");period.setOpenedAt(OffsetDateTime.parse("2027-07-02T10:00:00+08:00"));
        service.scanToday();assertEquals(2,notices.size());
        assertTrue(notices.stream().noneMatch(n->n.link().contains("kpi-results")));
    }

    @Test void unavailableConfigurationOrUnapprovedIndividualPlanDoesNotGenerateEmployeeNotices() {
        when(kpiAvailability.actionableSelfAssessmentForNotification(any(),any(),any())).thenReturn(false);
        period.setAttitudeConfiguration(null);
        service.scanToday();assertTrue(notices.stream().noneMatch(n->n.recipient().equals(bob.getId())));
    }

    @Test void completedAssessmentsAndPublishedCentralResultsAreSkipped() {
        savePending(participant);savedKpis.get(7L).get(0).setStatus(KpiAssessmentStatus.REVIEWED);savedAttitudes.get(7L).setStatus(AttitudeAssessmentStatus.REVIEWED);
        when(results.findByPlanVersionIdAndCheckpointIdAndStatus(anyLong(),anyLong(),eq(KpiCentralResultStatus.PUBLISHED))).thenReturn(Optional.of(new KpiCentralResult()));
        service.scanToday();assertTrue(notices.isEmpty());
    }

    @Test void removedPermissionRevokedDelegationAndOtherDepartmentCannotReceiveEntryNotices() {
        when(delegates.existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(1L,entrant.getId())).thenReturn(false);
        grants.get(md.getRole().getId()).remove(CAN_MANAGE_COMPANY_KPI);service.scanToday();
        var central=notices.stream().filter(n->n.link().contains("kpi-results")).toList();
        assertEquals(1,central.size());assertEquals(alice.getId(),central.get(0).recipient());
    }

    @Test void quarterlyOnlyPopulationStillGetsAprilCompanyEntryButNoEarlyQ2EmployeeNotice() {
        date("2027-05-01");when(kpiAvailability.actionableSelfAssessmentForNotification(any(),any(),any())).thenReturn(false);
        service.scanToday();assertEquals(2,notices.size());assertTrue(notices.stream().allMatch(n->n.link().contains("checkpointId=2")));
    }

    @Test void reviewerRemindersAreGroupedAcrossEmployeesAndRespectCurrentSavedRoute() {
        var carol=account("Carol",ROLE_USER);carol.setManager(alice);var p=participant(8L,carol);roster.add(p);savePending(p);savePending(participant);
        date("2027-07-11");service.scanToday();assertEquals(2,notices.size());
        assertTrue(notices.stream().allMatch(n->n.recipient().equals(alice.getId())));
        bob.setManager(hr);carol.setManager(hr);notices.clear();keys.clear();service.scanToday();assertTrue(notices.isEmpty());
    }

    @Test void readinessControlsAppraisalSubmissionRemindersWithoutCreatingDraftsOrChangingScores() {
        date("2027-07-18");service.scanToday();assertTrue(notices.isEmpty());
        when(readiness.readyForNotification(participant)).thenReturn(true);service.scanToday();
        assertEquals(1,notices.size());assertEquals(alice.getId(),notices.get(0).recipient());assertTrue(notices.get(0).link().contains("team-appraisals"));
        verify(appraisals,never()).save(any());verify(assessments,never()).save(any());
    }

    @Test void hrRemindersExcludeOwnAppraisalsAndSubmittersAndAllowReviewOfChangedResults() {
        var a=appraisal(AppraisalStatus.PENDING_REVIEW);savedAppraisals.put(7L,a);
        grants.get(bob.getRole().getId()).add(CAN_REVIEW_ANNUAL_APPRAISAL);grants.get(alice.getRole().getId()).add(CAN_REVIEW_ANNUAL_APPRAISAL);
        date("2027-07-25");service.scanToday();assertEquals(1,notices.size());assertEquals(hr.getId(),notices.get(0).recipient());
        assertTrue(notices.get(0).link().contains("appraisals/review"));verifyNoInteractions(readiness);
    }

    @Test void returnedAppraisalsRemindSuperiorWhileApprovedAndChangedRoutingAreReadOnly() {
        date("2027-07-18");when(readiness.readyForNotification(participant)).thenReturn(true);
        var a=appraisal(AppraisalStatus.RETURNED);savedAppraisals.put(7L,a);service.scanToday();assertEquals(1,notices.size());
        notices.clear();keys.clear();a.setStatus(AppraisalStatus.APPROVED);service.scanToday();assertTrue(notices.isEmpty());
        a.setStatus(AppraisalStatus.DRAFT);a.setSuperiorId(hr.getId());service.scanToday();assertTrue(notices.isEmpty());
    }

    @Test void closedPeriodsAndInactiveOrIneligibleParticipantsProduceNoTasks() {
        period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);service.scanToday();assertTrue(notices.isEmpty());
        period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);bob.setAccountStatus(StaffAccountStatus.INACTIVE);
        when(results.eligibleParticipants(anyLong(),anyLong())).thenReturn(0L);service.scanToday();assertTrue(notices.isEmpty());
        bob.setAccountStatus(StaffAccountStatus.ACTIVE);bob.getRole().setPerformanceReviewEligible(false);service.scanToday();assertTrue(notices.isEmpty());
    }

    @Test void upcomingPeriodsHaveSetupRemindersButNoAssessmentOrResultEntryNotices() {
        period.setStatus(AnnualKpiReviewPeriodStatus.UPCOMING);period.setKpiSetupDeadline(LocalDate.of(2027,7,4));
        when(periods.findAllByStatus(AnnualKpiReviewPeriodStatus.OPEN)).thenReturn(List.of());when(periods.findAllByStatus(AnnualKpiReviewPeriodStatus.UPCOMING)).thenReturn(List.of(period));
        periodPlans.clear();service.scanToday();assertEquals(3,notices.size());
        assertTrue(notices.stream().allMatch(n->n.type()==NotificationType.PERFORMANCE_REMINDER));verifyNoInteractions(kpiAvailability,results);
    }

    @Test void setupRemindersFollowPendingApprovalRatherThanAskingEmployeeToEditSubmittedPlan() {
        period.setKpiSetupDeadline(LocalDate.of(2027,7,4));var plan=periodPlans.get(2);plan.setStatus(KpiPlanStatus.PENDING_APPROVAL);plan.setSubmittedToSuperiorId(alice.getId());
        departmental.getPlan().setStatus(KpiPlanStatus.PENDING_APPROVAL);
        service.scanToday();var reminder=notices.stream().filter(n->n.type()==NotificationType.PERFORMANCE_REMINDER).toList();
        assertEquals(2,reminder.size());assertTrue(reminder.stream().noneMatch(n->n.recipient().equals(bob.getId())));
        assertTrue(reminder.stream().anyMatch(n->n.link().contains("tab=department")));assertTrue(reminder.stream().anyMatch(n->n.link().contains("tab=reviews")));
    }

    @Test void existingAssistedDraftRemindsAuthorisedSuperiorInsteadOfEmployee() {
        date("2027-07-04");period.setKpiSetupDeadline(LocalDate.of(2027,7,7));var plan=periodPlans.get(2);plan.setStatus(KpiPlanStatus.DRAFT);plan.setAssistanceAuthorizationId(30L);
        var request=new IndividualKpiAssistanceAuthorization();request.setSuperior(alice);request.setStatus(KpiAssistanceStatus.AUTHORIZED);when(assistance.findById(30L)).thenReturn(Optional.of(request));
        service.scanToday();assertTrue(notices.stream().anyMatch(n->n.recipient().equals(alice.getId()) && n.link().contains("tab=assistance")));
        assertTrue(notices.stream().noneMatch(n->n.recipient().equals(bob.getId()) && n.link().contains("my-kpi-plan")));
    }

    @Test void scannerDoesNotLockPublishCreateOrRecalculateWorkflowRecords() {
        service.scanToday();
        for(var repository:List.of(periods,participants,checkpoints,assessments,attitudes,appraisals,plans,versions,results,delegates,assistance))
            for(var call:mockingDetails(repository).getInvocations()) assertTrue(call.getMethod().getName().startsWith("find") || call.getMethod().getName().startsWith("exists") || call.getMethod().getName().equals("eligibleParticipants"),call.getMethod().getName());
        verify(notifications,never()).notify(any(),any(),any(),any(),any());
    }

    private void date(String date) {service=new KpiNotificationScheduleService(periods,participants,checkpoints,assessments,attitudes,appraisals,plans,versions,results,delegates,assistance,kpiAvailability,readiness,delivery,
            Clock.fixed(Instant.parse(date+"T01:00:00Z"),ZoneId.of("Asia/Singapore")));}
    private Staff account(String name,AuthorityName... permissions) {var r=new Role();r.setId((long)accounts.size()+1);var s=new Staff();s.setId(UUID.randomUUID());s.setName(name);s.setRole(r);s.setAccountStatus(StaffAccountStatus.ACTIVE);accounts.add(s);grants.put(r.getId(),new HashSet<>(List.of(permissions)));return s;}
    private ReviewPeriodParticipant participant(Long id,Staff s) {
        var p=new ReviewPeriodParticipant();p.setId(id);p.setReviewPeriod(period);p.setStaff(s);p.setRole(s.getRole());p.setDepartment(department);p.setReviewFrequency(ReviewFrequency.QUARTERLY);p.setCreatedAt(period.getParticipantsSnapshottedAt());
        var mapping=new AttitudeRoleFormatMapping();mapping.setRole(s.getRole());mapping.setEvaluationFormat(AttitudeEvaluationFormat.OTHERS);period.getAttitudeConfiguration().getRoleMappings().add(mapping);
        return p;
    }
    private ReviewCheckpoint checkpoint(Long id,ReviewFrequency frequency,String start,String end) {var c=new ReviewCheckpoint();c.setId(id);c.setReviewPeriod(period);c.setReviewFrequency(frequency);c.setStartDate(LocalDate.parse(start));c.setEndDate(LocalDate.parse(end));c.setSelfAssessmentDeadline(LocalDate.of(2027,7,7));c.setSuperiorAssessmentDeadline(LocalDate.of(2027,7,14));return c;}
    private KpiPlanVersion version(Long id,KpiLevel level) {var plan=new KpiPlan();plan.setId(id);plan.setReviewPeriod(period);plan.setLevel(level);plan.setDepartment(department);plan.setStatus(level==KpiLevel.COMPANY?KpiPlanStatus.PUBLISHED:KpiPlanStatus.APPROVED);var v=new KpiPlanVersion();v.setId(id);v.setPlan(plan);v.setLevel(level);v.setStatus(KpiVersionStatus.EFFECTIVE);return v;}
    private void savePending(ReviewPeriodParticipant p) {var k=new KpiAssessment();k.setId(p.getId()+100);k.setParticipant(p);k.setCheckpoint(quarter);k.setStatus(KpiAssessmentStatus.PENDING_REVIEW);k.setSubmittedToSuperiorId(alice.getId());k.getItems().add(new KpiAssessmentItem());savedKpis.put(p.getId(),List.of(k));var a=new AttitudeAssessment();a.setId(p.getId()+200);a.setParticipant(p);a.setStatus(AttitudeAssessmentStatus.PENDING_REVIEW);a.setSubmittedToSuperiorId(alice.getId());a.getItems().add(new AttitudeAssessmentItem());savedAttitudes.put(p.getId(),a);}
    private AnnualAppraisal appraisal(AppraisalStatus status) {var a=new AnnualAppraisal();a.setId(31L);a.setParticipant(participant);a.setEmployeeId(bob.getId());a.setSuperiorId(alice.getId());a.setStatus(status);return a;}
}
