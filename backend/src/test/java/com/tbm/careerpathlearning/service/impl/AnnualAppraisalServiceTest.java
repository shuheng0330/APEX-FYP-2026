package com.tbm.careerpathlearning.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.*;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnualAppraisalServiceTest {
    AnnualAppraisalRepository appraisals=mock(AnnualAppraisalRepository.class);
    AnnualAppraisalActivityRepository activity=mock(AnnualAppraisalActivityRepository.class);
    ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    StaffRepository staff=mock(StaffRepository.class);
    AnnualAppraisalReadinessService readiness=mock(AnnualAppraisalReadinessService.class);
    AnnualPerformanceHistoryService performanceHistory=mock(AnnualPerformanceHistoryService.class);
    KpiPlanMapper plans=mock(KpiPlanMapper.class);
    EmailService email=mock(EmailService.class);
    org.springframework.context.ApplicationEventPublisher notificationEvents=mock(org.springframework.context.ApplicationEventPublisher.class);
    UUID superiorId=UUID.randomUUID(),hrId=UUID.randomUUID(),employeeId=UUID.randomUUID(),outsiderId=UUID.randomUUID();
    Staff superior,employee;
    AnnualKpiReviewPeriod period;
    ReviewPeriodParticipant participant;
    AnnualAppraisal appraisal;
    AnnualAppraisalReadinessDto evaluation;
    List<AnnualAppraisalActivity> events=new ArrayList<>();
    AnnualAppraisalServiceImpl service;

    @BeforeEach void setup() {
        date("2028-02-01");auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");
        superior=actor(superiorId);actor(hrId);actor(outsiderId);employee=actor(employeeId);employee.setManager(superior);
        var role=new Role();role.setPerformanceReviewEligible(true);employee.setRole(role);
        period=new AnnualKpiReviewPeriod();period.setId(1L);period.setName("2027 Annual Review");period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        period.setStartDate(LocalDate.of(2027,1,1));period.setEndDate(LocalDate.of(2027,12,31));
        period.setAppraisalRecommendationDeadline(LocalDate.of(2028,1,15));period.setHrFinalisationDeadline(LocalDate.of(2028,1,25));
        participant=new ReviewPeriodParticipant();participant.setId(7L);participant.setStaff(employee);participant.setStaffName("Bob");
        participant.setReviewPeriod(period);participant.setReviewFrequency(ReviewFrequency.MONTHLY);
        when(participants.findById(7L)).thenReturn(Optional.of(participant));when(periods.findById(1L)).thenReturn(Optional.of(period));
        when(participants.findAllByReviewPeriodId(1L)).thenReturn(List.of(participant));
        evaluation=new AnnualAppraisalReadinessDto(true,List.of(),snapshot("80"));
        when(readiness.evaluate(participant)).thenAnswer(i->evaluation);
        when(appraisals.findByParticipantId(7L)).thenAnswer(i->Optional.ofNullable(appraisal));
        when(appraisals.findById(10L)).thenAnswer(i->Optional.ofNullable(appraisal));
        when(appraisals.lockById(10L)).thenAnswer(i->Optional.ofNullable(appraisal));
        when(appraisals.saveAndFlush(any())).thenAnswer(i->{AnnualAppraisal a=i.getArgument(0);
            if(a.getId()==null)a.setId(10L);else a.setLockVersion(a.getLockVersion()+1);appraisal=a;return a;});
        when(activity.save(any())).thenAnswer(i->{AnnualAppraisalActivity e=i.getArgument(0);e.setId((long)events.size()+1);events.add(e);return e;});
        when(activity.findAllByAppraisalIdOrderByIdAsc(10L)).thenAnswer(i->events);
    }
    void date(String day) {
        service=new AnnualAppraisalServiceImpl(appraisals,activity,participants,periods,staff,readiness,
                Mappers.getMapper(AnnualAppraisalMapper.class),plans,new ObjectMapper().registerModule(new JavaTimeModule()),
                email,Clock.fixed(Instant.parse(day+"T00:00:00Z"),ZoneId.of("Asia/Singapore")),performanceHistory,notificationEvents);
    }
    Staff actor(UUID id) {var s=new Staff();s.setId(id);s.setName("Actor");s.setEmail(id+"@example.test");
        s.setAccountStatus(StaffAccountStatus.ACTIVE);when(staff.findById(id)).thenReturn(Optional.of(s));return s;}
    void auth(UUID id,String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
    }
    AnnualAppraisalSnapshotDto snapshot(String score) {return new AnnualAppraisalSnapshotDto(7L,1L,employeeId,ReviewFrequency.MONTHLY,
            AnnualKpiConsolidationMethod.AVERAGE,new BigDecimal("50"),new BigDecimal("50"),new BigDecimal(score),
            new BigDecimal(score),new BigDecimal(score),List.of(),null);}
    AnnualAppraisalActionRequest action() {return new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,null);}
    AnnualAppraisalRequest request(AnnualAppraisalRecommendation recommendation,String justification) {
        return new AnnualAppraisalRequest(7L,appraisal==null?null:appraisal.getLockVersion(),recommendation,justification);
    }
    void submitted() {service.create(request(AnnualAppraisalRecommendation.SALARY_INCREMENT,"Consistent delivery"),superiorId);service.submit(10L,action(),superiorId);}

    @Test void notificationsUseSavedAuditIdsAndIgnoreDraftSavesAndReads() {
        service.create(request(AnnualAppraisalRecommendation.SALARY_INCREMENT,"Consistent delivery"),superiorId);
        service.update(10L,request(appraisal.getRecommendation(),"Consistent delivery"),superiorId);
        service.get(10L,superiorId);verifyNoInteractions(notificationEvents);
        service.submit(10L,action(),superiorId);
        hr();service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Clarify"),hrId);
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");
        service.update(10L,request(appraisal.getRecommendation(),"Updated evidence"),superiorId);service.submit(10L,action(),superiorId);
        var sent=org.mockito.ArgumentCaptor.forClass(KpiNotificationEvents.Appraisal.class);
        verify(notificationEvents,times(3)).publishEvent(sent.capture());
        assertEquals(List.of("SUBMITTED","RETURNED","RESUBMITTED"),sent.getAllValues().stream().map(e->e.activity().getAction()).toList());
        assertEquals(3,sent.getAllValues().stream().map(e->e.activity().getId()).distinct().count());
        assertTrue(sent.getAllValues().stream().allMatch(e->events.contains(e.activity())));
    }
    void hr() {auth(hrId,"CAN_REVIEW_ANNUAL_APPRAISAL");}
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization();}

    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void incompleteDraftIsSavedButCannotBeSubmitted(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        evaluation=new AnnualAppraisalReadinessDto(false,List.of("Waiting for Company results"),snapshot("80"));
        var dto=service.create(request(null,null),superiorId);assertEquals(AppraisalStatus.DRAFT,dto.getStatus());
        assertTrue(dto.getAllowedActions().contains("SAVE_DRAFT"));assertFalse(dto.getAllowedActions().contains("SUBMIT"));
        assertThrows(BadRequestException.class,()->service.submit(10L,action(),superiorId));assertNull(appraisal.getSnapshot());
        assertEquals(List.of("DRAFT_CREATED"),events.stream().map(AnnualAppraisalActivity::getAction).toList());
    }
    @ParameterizedTest @EnumSource(AnnualAppraisalRecommendation.class)
    void everyExplicitRecommendationCanBeSubmitted(AnnualAppraisalRecommendation recommendation) {
        service.create(request(recommendation,"Evidence-based recommendation"),superiorId);
        var dto=service.submit(10L,action(),superiorId);assertEquals(AppraisalStatus.PENDING_REVIEW,dto.getStatus());
        assertEquals(recommendation,dto.getRecommendation());assertEquals(evaluation.performance(),dto.getSnapshot());
        assertTrue(dto.getSubmittedLate());assertFalse(dto.getAllowedActions().contains("SAVE_DRAFT"));
        assertThrows(KpiVersionConflictException.class,()->service.submit(10L,action(),superiorId));
        assertThrows(KpiVersionConflictException.class,()->service.create(request(recommendation,"Duplicate"),superiorId));
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void approvalPreservesSuperiorContentAndFreezesTheOfficialSnapshot(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        submitted();var original=appraisal.getSnapshot();hr();var dto=service.approve(10L,action(),hrId);
        assertEquals(AppraisalStatus.APPROVED,dto.getStatus());assertEquals(dto.getRecommendation(),dto.getFinalDecision());
        assertEquals("Consistent delivery",dto.getJustification());assertEquals(hrId,dto.getReviewedBy());assertTrue(dto.getReviewedLate());
        evaluation=new AnnualAppraisalReadinessDto(true,List.of(),snapshot("90"));
        var frozen=service.get(10L,hrId);assertEquals(original,frozen.getSnapshot());assertFalse(frozen.isSourceChanged());
        assertTrue(frozen.getAllowedActions().isEmpty());
        assertThrows(KpiVersionConflictException.class,()->service.approve(10L,action(),hrId));
        assertThrows(KpiVersionConflictException.class,()->service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Again"),hrId));
        assertThrows(KpiVersionConflictException.class,()->service.overrideAndApprove(10L,
                new AnnualAppraisalActionRequest(appraisal.getLockVersion(),AnnualAppraisalRecommendation.PROMOTION,"Changed decision"),hrId));
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");
        assertThrows(KpiVersionConflictException.class,()->service.update(10L,request(null,null),superiorId));
        assertThrows(KpiVersionConflictException.class,()->service.submit(10L,action(),superiorId));
        assertEquals(original,appraisal.getSnapshot());assertEquals(dto.getFinalDecision(),appraisal.getFinalDecision());
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void overrideRequiresDifferentDecisionAndReasonAndRetainsOriginalRecommendation(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        submitted();hr();
        assertThrows(BadRequestException.class,()->service.overrideAndApprove(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),AnnualAppraisalRecommendation.SALARY_INCREMENT,"Reason"),hrId));
        assertThrows(BadRequestException.class,()->service.overrideAndApprove(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),AnnualAppraisalRecommendation.NO_RECOMMENDATION," "),hrId));
        var dto=service.overrideAndApprove(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),AnnualAppraisalRecommendation.NO_RECOMMENDATION,"Budget constraints"),hrId);
        assertEquals(AnnualAppraisalRecommendation.SALARY_INCREMENT,dto.getRecommendation());
        assertEquals(AnnualAppraisalRecommendation.NO_RECOMMENDATION,dto.getFinalDecision());assertEquals("Budget constraints",dto.getOverrideReason());
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void repeatedReturnsPreserveReasonsAndEverySubmissionSnapshot(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        submitted();hr();
        assertThrows(BadRequestException.class,()->service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null," "),hrId));
        for(int round=1;round<=2;round++) {
            hr();service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Clarify "+round),hrId);
            auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");
            assertThrows(BadRequestException.class,()->service.submit(10L,action(),superiorId));
            var current=appraisal.getJustification();service.update(10L,request(appraisal.getRecommendation()," "+current+" "),superiorId);
            assertThrows(BadRequestException.class,()->service.submit(10L,action(),superiorId));
            service.update(10L,request(appraisal.getRecommendation(),"Updated justification "+round),superiorId);
            service.submit(10L,action(),superiorId);
        }
        hr();service.approve(10L,action(),hrId);
        assertEquals("Clarify 2",appraisal.getLatestReturnReason());
        assertEquals(2,events.stream().filter(e->e.getAction().equals("RETURNED")).count());
        assertEquals(3,events.stream().filter(e->e.getAction().endsWith("SUBMITTED")).count());
        assertEquals("Consistent delivery",events.stream().filter(e->e.getAction().equals("SUBMITTED")).findFirst().orElseThrow().getJustification());
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void revertingEditsDoesNotSatisfyAReturnAndIncompleteReturnedEditsRemainSaveable(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        submitted();hr();service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Clarify"),hrId);
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");
        service.update(10L,request(null,null),superiorId);assertEquals(AppraisalStatus.RETURNED,appraisal.getStatus());
        service.update(10L,request(AnnualAppraisalRecommendation.SALARY_INCREMENT,"Consistent delivery"),superiorId);
        assertThrows(BadRequestException.class,()->service.submit(10L,action(),superiorId));
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void correctionsBlockApprovalButStillAllowReturn(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        submitted();hr();var original=appraisal.getSnapshot();evaluation=new AnnualAppraisalReadinessDto(true,List.of(),snapshot("90"));
        var dto=service.get(10L,hrId);assertTrue(dto.isSourceChanged());assertEquals(List.of("RETURN"),dto.getAllowedActions());
        assertThrows(KpiVersionConflictException.class,()->service.approve(10L,action(),hrId));assertEquals(original,appraisal.getSnapshot());
        service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Updated results require confirmation"),hrId);
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");service.update(10L,request(appraisal.getRecommendation(),"Confirmed corrected results"),superiorId);
        assertEquals(evaluation.performance(),service.submit(10L,action(),superiorId).getSnapshot());
        hr();assertDoesNotThrow(()->service.approve(10L,action(),hrId));
    }
    @Test void equivalentDecimalFormattingDoesNotCreateAFalseSourceChange() {
        submitted();evaluation=new AnnualAppraisalReadinessDto(true,List.of(),snapshot("80.0000"));hr();
        assertFalse(service.get(10L,hrId).isSourceChanged());
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void concurrentStaleActionsCannotUpdateOrDecideAgain(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        service.create(request(AnnualAppraisalRecommendation.PROMOTION,"Delivery"),superiorId);long stale=appraisal.getLockVersion();
        service.update(10L,request(appraisal.getRecommendation(),"Updated delivery"),superiorId);
        assertThrows(KpiVersionConflictException.class,()->service.update(10L,new AnnualAppraisalRequest(7L,stale,null,null),superiorId));
        assertThrows(KpiVersionConflictException.class,()->service.submit(10L,new AnnualAppraisalActionRequest(stale,null,null),superiorId));
        service.submit(10L,action(),superiorId);hr();long staleReview=appraisal.getLockVersion();
        service.approve(10L,action(),hrId);
        assertThrows(KpiVersionConflictException.class,()->service.overrideAndApprove(10L,new AnnualAppraisalActionRequest(staleReview,AnnualAppraisalRecommendation.BOTH,"Reason"),hrId));
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void unrelatedSuperiorsEmployeesAndLegacyAuthoritiesCannotWrite(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        auth(outsiderId,"CAN_SUBMIT_ANNUAL_APPRAISAL");assertThrows(AccessDeniedException.class,()->service.create(request(null,null),outsiderId));
        auth(employeeId,"ROLE_USER");assertThrows(AccessDeniedException.class,()->service.create(request(null,null),employeeId));
        auth(superiorId,"CAN_MANAGE_EVALUATION","CAN_MANAGE_EVALUATION_CYCLE");assertThrows(AccessDeniedException.class,()->service.create(request(null,null),superiorId));
        verify(appraisals,never()).saveAndFlush(any());
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void reportingChangesFreezeWritesForOldAndNewSuperiorsAndHr(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        submitted();employee.setManager(actor(outsiderId));hr();assertFalse(service.get(10L,hrId).getAllowedActions().contains("APPROVE"));
        assertThrows(KpiVersionConflictException.class,()->service.approve(10L,action(),hrId));
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");assertThrows(AccessDeniedException.class,()->service.update(10L,request(null,null),superiorId));
        auth(outsiderId,"CAN_SUBMIT_ANNUAL_APPRAISAL");assertThrows(AccessDeniedException.class,()->service.update(10L,request(null,null),outsiderId));
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void combinedPermissionsDoNotAllowSelfApproval(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        submitted();auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL","CAN_REVIEW_ANNUAL_APPRAISAL");
        assertThrows(AccessDeniedException.class,()->service.approve(10L,action(),superiorId));
        assertThrows(AccessDeniedException.class,()->service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Reason"),superiorId));
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void hrAuthorityDoesNotAllowAnEmployeeToReviewTheirOwnAppraisal(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        submitted();auth(employeeId,"CAN_REVIEW_ANNUAL_APPRAISAL");
        assertThrows(AccessDeniedException.class,()->service.approve(10L,action(),employeeId));
        assertTrue(service.get(10L,employeeId).getAllowedActions().isEmpty());
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"DRAFT","UPCOMING"})
    void draftAndUpcomingPeriodsRejectAllHrActions(AnnualKpiReviewPeriodStatus status) {
        submitted();period.setStatus(status);hr();assertTrue(service.get(10L,hrId).getAllowedActions().isEmpty());
        clearInvocations(appraisals,activity,notificationEvents);
        assertThrows(BadRequestException.class,()->service.approve(10L,action(),hrId));
        assertThrows(BadRequestException.class,()->service.overrideAndApprove(10L,
                new AnnualAppraisalActionRequest(appraisal.getLockVersion(),AnnualAppraisalRecommendation.PROMOTION,"Reason"),hrId));
        assertThrows(BadRequestException.class,()->service.returnForRevision(10L,
                new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Reason"),hrId));
        assertEquals(AppraisalStatus.PENDING_REVIEW,appraisal.getStatus());
        verify(appraisals,never()).saveAndFlush(any());verifyNoInteractions(activity,notificationEvents);
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"DRAFT","UPCOMING"})
    void draftAndUpcomingPeriodsRejectNewAndSavedSuperiorActions(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        assertThrows(BadRequestException.class,()->service.create(request(AnnualAppraisalRecommendation.PROMOTION,"Evidence"),superiorId));
        assertNull(appraisal);verify(appraisals,never()).saveAndFlush(any());
        period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        service.create(request(AnnualAppraisalRecommendation.PROMOTION,"Evidence"),superiorId);
        period.setStatus(status);var dto=service.get(10L,superiorId);
        assertTrue(dto.getAllowedActions().isEmpty());
        assertTrue(dto.getSubmissionBlockers().contains("Appraisals are actionable only while the Review Period is Open or Closed"));
        clearInvocations(appraisals,activity,notificationEvents);
        assertThrows(BadRequestException.class,()->service.update(10L,request(null,null),superiorId));
        assertThrows(BadRequestException.class,()->service.submit(10L,action(),superiorId));
        assertEquals(AppraisalStatus.DRAFT,appraisal.getStatus());assertEquals("Evidence",appraisal.getJustification());
        verify(appraisals,never()).saveAndFlush(any());verifyNoInteractions(activity,notificationEvents);
    }
    @Test void closingDuringTheAppraisalWorkflowPreservesActionsAndRecordedSubmissionSnapshots() {
        var draft=service.create(request(AnnualAppraisalRecommendation.SALARY_INCREMENT,"Consistent delivery"),superiorId);
        period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertEquals(Set.of("SAVE_DRAFT","SUBMIT"),new HashSet<>(service.get(10L,superiorId).getAllowedActions()));
        service.submit(10L,action(),superiorId);var original=appraisal.getSnapshot();hr();
        assertEquals(Set.of("RETURN","APPROVE","OVERRIDE_AND_APPROVE"),new HashSet<>(service.get(10L,hrId).getAllowedActions()));
        service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Add context"),hrId);
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");
        assertEquals(List.of("SAVE_DRAFT"),service.get(10L,superiorId).getAllowedActions());
        service.update(10L,request(AnnualAppraisalRecommendation.PROMOTION,"Confirmed delivery with additional context"),superiorId);
        assertEquals(Set.of("SAVE_DRAFT","SUBMIT"),new HashSet<>(service.get(10L,superiorId).getAllowedActions()));
        service.submit(10L,action(),superiorId);hr();
        var approved=service.overrideAndApprove(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),
                AnnualAppraisalRecommendation.NO_RECOMMENDATION,"Documented HR decision"),hrId);
        assertEquals(AppraisalStatus.APPROVED,approved.getStatus());assertEquals(draft.getId(),approved.getId());
        assertEquals(AnnualAppraisalRecommendation.PROMOTION,approved.getRecommendation());
        assertEquals(AnnualAppraisalRecommendation.NO_RECOMMENDATION,approved.getFinalDecision());
        assertEquals(original,approved.getSnapshot());assertTrue(approved.getAllowedActions().isEmpty());
        assertEquals(List.of("DRAFT_CREATED","SUBMITTED","RETURNED","DRAFT_SAVED","RESUBMITTED","OVERRIDDEN"),
                events.stream().map(AnnualAppraisalActivity::getAction).toList());
        var first=events.get(1);assertEquals("Consistent delivery",first.getJustification());
        assertEquals(AnnualAppraisalRecommendation.SALARY_INCREMENT,first.getRecommendation());assertEquals(original,first.getSnapshot());
        assertNull(first.getFinalDecision());assertNull(events.get(4).getFinalDecision());
        assertEquals(original,events.get(4).getSnapshot());assertEquals("Add context",events.get(2).getReason());
    }
    @Test void closedPeriodMutationsRetainConfigurationAndAppraisalLockOrder() {
        period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        service.create(request(AnnualAppraisalRecommendation.SALARY_INCREMENT,"Consistent delivery"),superiorId);
        service.update(10L,request(appraisal.getRecommendation(),"Updated delivery"),superiorId);
        service.submit(10L,action(),superiorId);hr();
        service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Clarify"),hrId);
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");
        service.update(10L,request(appraisal.getRecommendation(),"Additional context"),superiorId);
        service.submit(10L,action(),superiorId);hr();service.approve(10L,action(),hrId);
        var order=inOrder(periods,appraisals);
        order.verify(periods).lockConfiguration();order.verify(appraisals).saveAndFlush(appraisal);
        for(int i=0;i<6;i++) {
            order.verify(periods).lockConfiguration();order.verify(appraisals).lockById(10L);
            order.verify(appraisals).saveAndFlush(appraisal);
        }
        verify(periods,times(7)).lockConfiguration();verify(appraisals,times(6)).lockById(10L);
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void inactiveEmployeesAndMissingDeadlinesStillBlockActions(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);employee.setAccountStatus(StaffAccountStatus.INACTIVE);
        assertThrows(BadRequestException.class,()->service.create(request(null,null),superiorId));
        assertNull(appraisal);employee.setAccountStatus(StaffAccountStatus.ACTIVE);
        service.create(request(AnnualAppraisalRecommendation.SALARY_INCREMENT,"Evidence"),superiorId);
        period.setAppraisalRecommendationDeadline(null);
        assertFalse(service.get(10L,superiorId).getAllowedActions().contains("SUBMIT"));
        assertThrows(BadRequestException.class,()->service.submit(10L,action(),superiorId));
        period.setAppraisalRecommendationDeadline(LocalDate.of(2028,1,15));service.submit(10L,action(),superiorId);hr();
        period.setHrFinalisationDeadline(null);assertTrue(service.get(10L,hrId).getAllowedActions().isEmpty());
        assertThrows(BadRequestException.class,()->service.approve(10L,action(),hrId));
        assertThrows(BadRequestException.class,()->service.returnForRevision(10L,
                new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Clarify"),hrId));
    }
    @Test void deadlineDayIsOnTimeAndLaterActionsAreAllowed() {
        date("2028-01-15");submitted();assertFalse(appraisal.getSubmittedLate());
        date("2028-01-25");hr();service.approve(10L,action(),hrId);assertFalse(appraisal.getReviewedLate());
    }
    @Test void notificationsRunOnlyAfterCommitAndDoNotChangeTheDecision() {
        submitted();hr();TransactionSynchronizationManager.initSynchronization();service.approve(10L,action(),hrId);
        verifyNoInteractions(email);
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());
        verify(email).sendAnnualAppraisalReviewedEmail(superior.getEmail(),"Bob",period.getName(),"APPROVED",Locale.ENGLISH);
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void hrCannotReadPrivateDraftAndEmployeesCannotReadAnyAppraisal(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        service.create(request(null,null),superiorId);hr();assertThrows(AccessDeniedException.class,()->service.get(10L,hrId));
        auth(employeeId,"ROLE_USER");assertThrows(AccessDeniedException.class,()->service.get(10L,employeeId));
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"OPEN","CLOSED"})
    void readingReadinessAndTeamDoesNotCreateDrafts(AnnualKpiReviewPeriodStatus status) {
        period.setStatus(status);
        assertTrue(service.readiness(7L,superiorId).ready());assertEquals(1,service.team(1L,superiorId).size());
        assertNull(appraisal);assertNull(service.team(1L,superiorId).get(0).appraisalId());
        verify(appraisals,never()).saveAndFlush(any());verify(activity,never()).save(any());
        verify(periods,never()).lockConfiguration();verifyNoInteractions(notificationEvents,email);
    }
    @Test void annualHistoryIsScopedApprovedOnlyAndUsesFrozenScores() {
        submitted();hr();service.approve(10L,action(),hrId);
        var draft=new AnnualAppraisal();draft.setStatus(AppraisalStatus.DRAFT);
        when(appraisals.findAllByEmployeeIdOrderBySubmittedAtDescIdDesc(employeeId)).thenReturn(List.of(appraisal,draft));
        clearInvocations(readiness);
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");var history=service.history(7L,superiorId);
        assertEquals(1,history.size());assertEquals(appraisal.getSnapshot(),history.get(0).getSnapshot());
        verify(readiness,never()).evaluate(any());
        auth(outsiderId,"CAN_SUBMIT_ANNUAL_APPRAISAL");assertThrows(AccessDeniedException.class,()->service.history(7L,outsiderId));
        auth(employeeId,"ROLE_USER");assertThrows(AccessDeniedException.class,()->service.history(7L,employeeId));
        hr();assertEquals(1,service.history(7L,hrId).size());
    }
    @Test void draftTextLimitIsCheckedBeforeAnySave() {
        assertThrows(BadRequestException.class,()->service.create(request(null,"x".repeat(10001)),superiorId));
        verify(appraisals,never()).saveAndFlush(any());
    }

    @Test void performanceHistoryUsesExistingHistoryScopeWithoutCreatingOrChangingAppraisals() {
        when(performanceHistory.read(participant)).thenReturn(List.of());
        assertEquals(List.of(),service.performanceHistory(7L,superiorId));
        hr();assertEquals(List.of(),service.performanceHistory(7L,hrId));
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL","CAN_REVIEW_ANNUAL_APPRAISAL");
        service.performanceHistory(7L,superiorId);
        verify(performanceHistory,times(3)).read(participant);
        auth(outsiderId,"CAN_SUBMIT_ANNUAL_APPRAISAL");
        assertThrows(AccessDeniedException.class,()->service.performanceHistory(7L,outsiderId));
        auth(employeeId,"ROLE_USER");
        assertThrows(AccessDeniedException.class,()->service.performanceHistory(7L,employeeId));
        employee.setManager(actor(outsiderId));auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");
        assertThrows(AccessDeniedException.class,()->service.performanceHistory(7L,superiorId));
        verifyNoMoreInteractions(performanceHistory);verifyNoInteractions(email,activity);
        verify(appraisals,never()).saveAndFlush(any());verify(periods,never()).lockConfiguration();
    }

    @Test void performanceHistoryRejectsMissingDraftAndInactiveContextsBeforeReadingHistory() {
        assertThrows(BadRequestException.class,()->service.performanceHistory(999L,superiorId));
        period.setStatus(AnnualKpiReviewPeriodStatus.DRAFT);
        assertThrows(BadRequestException.class,()->service.performanceHistory(7L,superiorId));
        period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);superior.setAccountStatus(StaffAccountStatus.INACTIVE);
        assertThrows(AccessDeniedException.class,()->service.performanceHistory(7L,superiorId));
        verifyNoInteractions(performanceHistory);
    }
}
