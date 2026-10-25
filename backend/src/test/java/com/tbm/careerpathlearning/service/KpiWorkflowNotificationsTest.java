package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.KpiRevisionDecisionDto;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static com.tbm.careerpathlearning.enums.AuthorityName.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KpiWorkflowNotificationsTest {
    NotificationService notifications=mock(NotificationService.class);
    StaffRepository staff=mock(StaffRepository.class);
    RoleAuthorityRepository authorities=mock(RoleAuthorityRepository.class);
    ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    AnnualAppraisalRepository appraisals=mock(AnnualAppraisalRepository.class);
    AnnualAppraisalReadinessService readiness=mock(AnnualAppraisalReadinessService.class);
    PerformanceDepartmentResolver departments=mock(PerformanceDepartmentResolver.class);
    KpiWorkflowNotifications service=new KpiWorkflowNotifications(notifications,staff,authorities,participants,appraisals,readiness,departments);
    final List<Staff> accounts=new ArrayList<>();
    final Map<Long,Set<AuthorityName>> grants=new HashMap<>();
    final Set<String> sentKeys=new HashSet<>();
    final List<Notice> notices=new ArrayList<>();
    record Notice(String key,UUID recipient,NotificationType type,String title,String body,String link) {}
    Staff bob,alice,hr,md,other;
    OrgChart department;
    AnnualKpiReviewPeriod period;
    ReviewPeriodParticipant participant;

    @BeforeEach void setup() {
        bob=account("Bob",ROLE_USER); alice=account("Alice",ROLE_USER,CAN_REVIEW_INDIVIDUAL_KPI,CAN_REVIEW_KPI_ASSESSMENT,CAN_REVIEW_ATTITUDE_EVALUATION,CAN_SUBMIT_ANNUAL_APPRAISAL,CAN_MANAGE_DEPARTMENT_KPI);
        hr=account("HR",ROLE_USER,CAN_REVIEW_ANNUAL_APPRAISAL,CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE,CAN_PREPARE_DEPARTMENT_KPI_REVISION,CAN_MANAGE_INDIVIDUAL_KPI_REVISIONS);
        md=account("MD",ROLE_USER,CAN_APPROVE_DEPARTMENT_KPI); other=account("Other employee",ROLE_USER);
        bob.setManager(alice);
        department=new OrgChart();department.setId(10L);department.setName("IT");
        when(departments.resolve(alice.getRole())).thenReturn(department);
        period=new AnnualKpiReviewPeriod();period.setId(2L);period.setName("2026 Annual Review");period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        participant=new ReviewPeriodParticipant();participant.setId(7L);participant.setStaff(bob);participant.setStaffName("Bob");participant.setReviewPeriod(period);participant.setDepartment(department);
        when(staff.findAllActiveWithRole()).thenAnswer(i->accounts);
        when(staff.findById(any())).thenAnswer(i->accounts.stream().filter(s->s.getId().equals(i.getArgument(0))).findFirst());
        when(authorities.existsByRoleIdAndAuthorityName(anyLong(),any())).thenAnswer(i->grants.getOrDefault(i.getArgument(0),Set.of()).contains(i.getArgument(1)));
        when(readiness.readyForNotification(participant)).thenReturn(false);
        when(notifications.notifyInAppOnce(anyString(),any(),any(),anyString(),anyString(),anyString())).thenAnswer(i->{
            String key=i.getArgument(0);if(!sentKeys.add(key))return false;
            notices.add(new Notice(key,i.getArgument(1),i.getArgument(2),i.getArgument(3),i.getArgument(4),i.getArgument(5)));return true;
        });
    }

    @Test void enrolledEmployeeGetsOneScheduleNotificationWithoutEmailOrDraftCreation() {
        var event=new KpiNotificationEvents.Enrolled(List.of(participant),hr.getId());
        service.enrolled(event);service.enrolled(event);
        assertEquals(1,notices.size());assertEquals(bob.getId(),notices.get(0).recipient());
        assertEquals("/my-performance/my-kpi-plan?reviewPeriodId=2",notices.get(0).link());
        verify(notifications,never()).notify(any(),any(),any(),any(),any());
        verifyNoInteractions(appraisals,readiness);
    }

    @Test void individualSubmissionsUseCurrentSuperiorAndEverySuccessfulResubmissionHasItsOwnEvent() {
        var plan=plan(KpiLevel.INDIVIDUAL);plan.setSubmittedToSuperiorId(alice.getId());
        var first=new KpiNotificationEvents.Plan(plan,"SUBMITTED",bob.getId());
        service.plan(first);service.plan(first);service.plan(new KpiNotificationEvents.Plan(plan,"SUBMITTED",bob.getId()));
        assertEquals(2,notices.size());assertTrue(notices.stream().allMatch(n->n.recipient().equals(alice.getId())));
        assertTrue(notices.get(0).link().contains("tab=reviews&planId=11"));
        bob.setManager(other);service.plan(new KpiNotificationEvents.Plan(plan,"SUBMITTED",bob.getId()));
        assertEquals(2,notices.size());
    }

    @Test void permissionsActiveAccountsAndSelfSuppressionAreAppliedAtDelivery() {
        var plan=plan(KpiLevel.INDIVIDUAL);plan.setSubmittedToSuperiorId(alice.getId());
        grants.get(alice.getRole().getId()).remove(CAN_REVIEW_INDIVIDUAL_KPI);
        service.plan(new KpiNotificationEvents.Plan(plan,"SUBMITTED",bob.getId()));assertTrue(notices.isEmpty());
        grants.get(alice.getRole().getId()).add(CAN_REVIEW_INDIVIDUAL_KPI);alice.setAccountStatus(StaffAccountStatus.INACTIVE);
        service.plan(new KpiNotificationEvents.Plan(plan,"SUBMITTED",bob.getId()));assertTrue(notices.isEmpty());
        alice.setAccountStatus(StaffAccountStatus.ACTIVE);service.plan(new KpiNotificationEvents.Plan(plan,"SUBMITTED",alice.getId()));
        assertTrue(notices.isEmpty());
    }

    @Test void noBaseUserPermissionOrDeletedRoleCannotReceiveBellMessages() {
        bob.getRole().setDeleted(true);service.plan(new KpiNotificationEvents.Plan(plan(KpiLevel.INDIVIDUAL),"APPROVED",alice.getId()));
        bob.getRole().setDeleted(false);grants.get(bob.getRole().getId()).clear();
        service.plan(new KpiNotificationEvents.Plan(plan(KpiLevel.INDIVIDUAL),"APPROVED",alice.getId()));
        assertTrue(notices.isEmpty());
    }

    @Test void departmentOutcomesOnlyReachAuthorisedOwnersOfThatDepartment() {
        var plan=plan(KpiLevel.DEPARTMENT);
        grants.get(other.getRole().getId()).add(CAN_MANAGE_DEPARTMENT_KPI);
        var otherDepartment=new OrgChart();otherDepartment.setId(12L);when(departments.resolve(other.getRole())).thenReturn(otherDepartment);
        service.plan(new KpiNotificationEvents.Plan(plan,"RETURNED",md.getId()));
        assertEquals(List.of(alice.getId()),notices.stream().map(Notice::recipient).toList());
        assertFalse(notices.get(0).body().contains("reason"));
    }

    @Test void companyPublicationOnlyAlertsParticipatingDepartmentOwnersWhoCanViewAssignedPlans() {
        var aliceParticipant=new ReviewPeriodParticipant();aliceParticipant.setId(8L);aliceParticipant.setStaff(alice);aliceParticipant.setDepartment(department);aliceParticipant.setReviewPeriod(period);
        when(participants.findAllByReviewPeriodId(2L)).thenReturn(List.of(participant,aliceParticipant));
        service.plan(new KpiNotificationEvents.Plan(plan(KpiLevel.COMPANY),"PUBLISHED",md.getId()));
        assertEquals(1,notices.size());assertEquals(alice.getId(),notices.get(0).recipient());
    }

    @Test void draftEventsAreIgnoredAndApprovedIndividualOutcomeReachesOwner() {
        var plan=plan(KpiLevel.INDIVIDUAL);
        service.plan(new KpiNotificationEvents.Plan(plan,"DRAFT_SAVED",bob.getId()));assertTrue(notices.isEmpty());
        service.plan(new KpiNotificationEvents.Plan(plan,"CONFIRMED",alice.getId()));
        assertEquals(bob.getId(),notices.get(0).recipient());assertTrue(notices.get(0).body().contains("confirmed"));
    }

    @Test void assistanceRoutesToAuthorisersThenStillAuthorisedRequestingSuperior() {
        var request=new IndividualKpiAssistanceAuthorization();request.setId(5L);request.setOwnerParticipant(participant);request.setSuperior(alice);
        service.assistance(new KpiNotificationEvents.Assistance(request,alice.getId()));
        assertEquals(hr.getId(),notices.get(0).recipient());
        request.setStatus(KpiAssistanceStatus.AUTHORIZED);service.assistance(new KpiNotificationEvents.Assistance(request,hr.getId()));
        assertEquals(alice.getId(),notices.get(1).recipient());
        bob.setManager(other);request.setStatus(KpiAssistanceStatus.REJECTED);service.assistance(new KpiNotificationEvents.Assistance(request,hr.getId()));
        assertEquals(2,notices.size());
    }

    @Test void revisionRoutesRespectSeparatePreparerApproverAndRequestingHod() {
        var v=revision(KpiLevel.DEPARTMENT);v.setRequestedBy(alice.getId());
        service.revision(new KpiNotificationEvents.Revision(v,alice.getId()));assertEquals(hr.getId(),notices.get(0).recipient());
        v.setPreparedBy(hr.getId());v.setStatus(KpiVersionStatus.PENDING_MD_APPROVAL);transition(v);
        grants.get(hr.getRole().getId()).add(CAN_APPROVE_DEPARTMENT_KPI);grants.get(alice.getRole().getId()).add(CAN_APPROVE_DEPARTMENT_KPI);
        service.revision(new KpiNotificationEvents.Revision(v,hr.getId()));assertEquals(md.getId(),notices.get(1).recipient());
        v.setStatus(KpiVersionStatus.PENDING_HOD_CONFIRMATION);transition(v);service.revision(new KpiNotificationEvents.Revision(v,md.getId()));
        assertEquals(alice.getId(),notices.get(2).recipient());
    }

    @Test void repeatedRevisionReturnsUseHistoryPositionEvenWithIdenticalTimestamps() {
        var v=revision(KpiLevel.DEPARTMENT);v.setPreparedBy(hr.getId());v.setStatus(KpiVersionStatus.RETURNED);
        transition(v);service.revision(new KpiNotificationEvents.Revision(v,md.getId()));service.revision(new KpiNotificationEvents.Revision(v,md.getId()));
        transition(v);service.revision(new KpiNotificationEvents.Revision(v,md.getId()));
        assertEquals(2,notices.size());assertNotEquals(notices.get(0).key(),notices.get(1).key());
    }

    @Test void individualRevisionHonoursPreparationModeAndRecordedReportingRoute() {
        var v=revision(KpiLevel.INDIVIDUAL);v.setRevisionSuperiorId(alice.getId());v.setPreparationMode(KpiRevisionPreparationMode.HR_MD);
        service.revision(new KpiNotificationEvents.Revision(v,hr.getId()));assertTrue(notices.isEmpty());
        v.setStatus(KpiVersionStatus.PENDING_APPROVAL);transition(v);service.revision(new KpiNotificationEvents.Revision(v,hr.getId()));
        assertEquals(alice.getId(),notices.get(0).recipient());
        bob.setManager(other);transition(v);service.revision(new KpiNotificationEvents.Revision(v,hr.getId()));assertEquals(1,notices.size());
    }

    @Test void effectiveIndividualRevisionTellsEmployeeWithoutExposingRevisionReasons() {
        var v=revision(KpiLevel.INDIVIDUAL);v.setStatus(KpiVersionStatus.EFFECTIVE);v.setRevisionReason("Confidential rationale");
        service.revision(new KpiNotificationEvents.Revision(v,alice.getId()));assertEquals(bob.getId(),notices.get(0).recipient());
        assertFalse(notices.get(0).body().contains("Confidential"));
    }

    @Test void kpiReviewCanCompleteWhileCentralResultsAreMissing() {
        var a=assessment();a.setStatus(KpiAssessmentStatus.PENDING_REVIEW);service.kpiReview(new KpiNotificationEvents.KpiReview(a,bob.getId()));
        assertEquals(alice.getId(),notices.get(0).recipient());
        a.setStatus(KpiAssessmentStatus.REVIEWED);service.kpiReview(new KpiNotificationEvents.KpiReview(a,alice.getId()));
        assertEquals(bob.getId(),notices.get(1).recipient());assertNull(a.getCheckpointScore());
    }

    @Test void combinedReviewAndAvailableScoreProducesOneEmployeeNotificationIncludingLegitimateZero() {
        var a=assessment();a.setCheckpointScore(BigDecimal.ZERO);
        service.score(new KpiNotificationEvents.Score(a,null,1,alice.getId(),true));
        service.kpiReview(new KpiNotificationEvents.KpiReview(a,alice.getId()));
        assertEquals(1,notices.size());assertTrue(notices.get(0).body().contains("review is complete"));
        assertTrue(notices.get(0).link().contains("checkpointId=6"));assertTrue(notices.get(0).link().endsWith("showBreakdown=true"));
    }

    @Test void correctedScoresNotifyOnlyForNumericChangesWithoutExposingValues() {
        var a=assessment();a.setCheckpointScore(new BigDecimal("80.0000"));
        service.score(new KpiNotificationEvents.Score(a,new BigDecimal("80.0"),2,md.getId(),false));assertTrue(notices.isEmpty());
        service.score(new KpiNotificationEvents.Score(a,new BigDecimal("79"),3,md.getId(),false));
        assertEquals(1,notices.size());assertFalse(notices.get(0).body().contains("80"));assertTrue(notices.get(0).body().contains("updated"));
        a.setCheckpointScore(null);service.score(new KpiNotificationEvents.Score(a,BigDecimal.ZERO,4,md.getId(),false));assertEquals(1,notices.size());
    }

    @Test void readinessUsesExistingCalculationAndDoesNotNotifyPendingOrApprovedAppraisals() {
        when(readiness.readyForNotification(participant)).thenReturn(true);
        var a=assessment();a.setCheckpointScore(BigDecimal.ZERO);
        service.score(new KpiNotificationEvents.Score(a,null,1,md.getId(),false));
        assertTrue(notices.stream().anyMatch(n->n.recipient().equals(alice.getId()) && n.body().contains("ready for appraisal")));
        var appraisal=appraisal();appraisal.setStatus(AppraisalStatus.APPROVED);when(appraisals.findByParticipantId(7L)).thenReturn(Optional.of(appraisal));
        notices.clear();service.score(new KpiNotificationEvents.Score(a,BigDecimal.ONE,2,md.getId(),false));
        assertEquals(1,notices.size());assertEquals(bob.getId(),notices.get(0).recipient());
    }

    @Test void attitudeSubmissionAndCompletedResultsUseExistingRouteWithoutScoreOrComments() {
        var a=new AttitudeAssessment();a.setId(15L);a.setParticipant(participant);a.setSubmittedToSuperiorId(alice.getId());
        a.setStatus(AttitudeAssessmentStatus.PENDING_REVIEW);service.attitudeReview(new KpiNotificationEvents.AttitudeReview(a,bob.getId()));
        assertEquals(alice.getId(),notices.get(0).recipient());
        a.setStatus(AttitudeAssessmentStatus.REVIEWED);service.attitudeReview(new KpiNotificationEvents.AttitudeReview(a,alice.getId()));
        assertEquals(bob.getId(),notices.get(1).recipient());assertTrue(notices.get(1).link().contains("tab=attitude"));
    }

    @Test void appraisalReviewExcludesEmployeeSubmitterAndActorRegardlessOfSharedPermissions() {
        grants.get(bob.getRole().getId()).add(CAN_REVIEW_ANNUAL_APPRAISAL);grants.get(alice.getRole().getId()).add(CAN_REVIEW_ANNUAL_APPRAISAL);
        var a=appraisal();service.appraisal(new KpiNotificationEvents.Appraisal(a,activity(1L,"SUBMITTED"),alice.getId()));
        assertEquals(1,notices.size());assertEquals(hr.getId(),notices.get(0).recipient());assertEquals("/appraisals/review/20",notices.get(0).link());
    }

    @Test void eachAppraisalReturnAndResubmissionUsesTheSavedActivityId() {
        var a=appraisal();service.appraisal(new KpiNotificationEvents.Appraisal(a,activity(1L,"SUBMITTED"),alice.getId()));
        service.appraisal(new KpiNotificationEvents.Appraisal(a,activity(2L,"RETURNED"),hr.getId()));
        service.appraisal(new KpiNotificationEvents.Appraisal(a,activity(3L,"RESUBMITTED"),alice.getId()));
        service.appraisal(new KpiNotificationEvents.Appraisal(a,activity(4L,"RETURNED"),hr.getId()));
        assertEquals(4,notices.size());assertNotEquals(notices.get(1).key(),notices.get(3).key());
    }

    @Test void finalOutcomeOnlyReachesSuperiorAndContainsNoDecisionReasonsOrScores() {
        var a=appraisal();a.setFinalDecision(AnnualAppraisalRecommendation.PROMOTION);a.setOverrideReason("Sensitive override");
        service.appraisal(new KpiNotificationEvents.Appraisal(a,activity(4L,"OVERRIDDEN"),hr.getId()));
        assertEquals(1,notices.size());assertEquals(alice.getId(),notices.get(0).recipient());
        assertEquals("HR has finalised an appraisal.",notices.get(0).body());
        bob.setManager(other);service.appraisal(new KpiNotificationEvents.Appraisal(a,activity(5L,"APPROVED"),hr.getId()));
        assertEquals(1,notices.size());
    }

    private Staff account(String name,AuthorityName... permissions) {
        var r=new Role();r.setId((long)accounts.size()+1);var s=new Staff();s.setId(UUID.randomUUID());s.setRole(r);s.setName(name);s.setAccountStatus(StaffAccountStatus.ACTIVE);
        accounts.add(s);grants.put(r.getId(),new HashSet<>(List.of(permissions)));return s;
    }
    private KpiPlan plan(KpiLevel level) {
        var p=new KpiPlan();p.setId(11L);p.setReviewPeriod(period);p.setLevel(level);p.setDepartment(department);p.setOwnerParticipant(participant);return p;
    }
    private KpiPlanVersion revision(KpiLevel level) {
        var v=new KpiPlanVersion();v.setId(12L);v.setPlan(plan(level));v.setLevel(level);v.setStatus(KpiVersionStatus.REQUESTED);return v;
    }
    private void transition(KpiPlanVersion v) {v.getDecisionHistory().add(new KpiRevisionDecisionDto("TEST",hr.getId(),null,null,v.getStatus(),null));}
    private KpiAssessment assessment() {
        var a=new KpiAssessment();a.setId(9L);a.setParticipant(participant);a.setAssessmentModel(KpiAssessmentModel.CENTRAL_RESULTS);a.setStatus(KpiAssessmentStatus.REVIEWED);
        a.setSubmittedToSuperiorId(alice.getId());var c=new ReviewCheckpoint();c.setId(6L);c.setStartDate(LocalDate.of(2026,4,1));c.setEndDate(LocalDate.of(2026,6,30));a.setCheckpoint(c);return a;
    }
    private AnnualAppraisal appraisal() {
        var a=new AnnualAppraisal();a.setId(20L);a.setParticipant(participant);a.setEmployeeId(bob.getId());a.setSuperiorId(alice.getId());return a;
    }
    private AnnualAppraisalActivity activity(Long id,String action) {var e=new AnnualAppraisalActivity();e.setId(id);e.setAction(action);return e;}
}
