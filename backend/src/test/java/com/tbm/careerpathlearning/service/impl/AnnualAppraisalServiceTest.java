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
                email,Clock.fixed(Instant.parse(day+"T00:00:00Z"),ZoneId.of("Asia/Singapore")),performanceHistory);
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
    void hr() {auth(hrId,"CAN_REVIEW_ANNUAL_APPRAISAL");}
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization();}

    @Test void incompleteDraftIsSavedButCannotBeSubmitted() {
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
    @Test void approvalPreservesSuperiorContentAndFreezesTheOfficialSnapshot() {
        submitted();var original=appraisal.getSnapshot();hr();var dto=service.approve(10L,action(),hrId);
        assertEquals(AppraisalStatus.APPROVED,dto.getStatus());assertEquals(dto.getRecommendation(),dto.getFinalDecision());
        assertEquals("Consistent delivery",dto.getJustification());assertEquals(hrId,dto.getReviewedBy());assertTrue(dto.getReviewedLate());
        evaluation=new AnnualAppraisalReadinessDto(true,List.of(),snapshot("90"));
        var frozen=service.get(10L,hrId);assertEquals(original,frozen.getSnapshot());assertFalse(frozen.isSourceChanged());
        assertTrue(frozen.getAllowedActions().isEmpty());
        assertThrows(KpiVersionConflictException.class,()->service.approve(10L,action(),hrId));
        assertThrows(KpiVersionConflictException.class,()->service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Again"),hrId));
    }
    @Test void overrideRequiresDifferentDecisionAndReasonAndRetainsOriginalRecommendation() {
        submitted();hr();
        assertThrows(BadRequestException.class,()->service.overrideAndApprove(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),AnnualAppraisalRecommendation.SALARY_INCREMENT,"Reason"),hrId));
        assertThrows(BadRequestException.class,()->service.overrideAndApprove(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),AnnualAppraisalRecommendation.NO_RECOMMENDATION," "),hrId));
        var dto=service.overrideAndApprove(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),AnnualAppraisalRecommendation.NO_RECOMMENDATION,"Budget constraints"),hrId);
        assertEquals(AnnualAppraisalRecommendation.SALARY_INCREMENT,dto.getRecommendation());
        assertEquals(AnnualAppraisalRecommendation.NO_RECOMMENDATION,dto.getFinalDecision());assertEquals("Budget constraints",dto.getOverrideReason());
    }
    @Test void repeatedReturnsPreserveReasonsAndEverySubmissionSnapshot() {
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
    @Test void revertingEditsDoesNotSatisfyAReturnAndIncompleteReturnedEditsRemainSaveable() {
        submitted();hr();service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Clarify"),hrId);
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");
        service.update(10L,request(null,null),superiorId);assertEquals(AppraisalStatus.RETURNED,appraisal.getStatus());
        service.update(10L,request(AnnualAppraisalRecommendation.SALARY_INCREMENT,"Consistent delivery"),superiorId);
        assertThrows(BadRequestException.class,()->service.submit(10L,action(),superiorId));
    }
    @Test void correctionsBlockApprovalButStillAllowReturn() {
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
    @Test void concurrentStaleActionsCannotUpdateOrDecideAgain() {
        service.create(request(AnnualAppraisalRecommendation.PROMOTION,"Delivery"),superiorId);long stale=appraisal.getLockVersion();
        service.update(10L,request(appraisal.getRecommendation(),"Updated delivery"),superiorId);
        assertThrows(KpiVersionConflictException.class,()->service.update(10L,new AnnualAppraisalRequest(7L,stale,null,null),superiorId));
        assertThrows(KpiVersionConflictException.class,()->service.submit(10L,new AnnualAppraisalActionRequest(stale,null,null),superiorId));
        service.submit(10L,action(),superiorId);hr();long staleReview=appraisal.getLockVersion();
        service.approve(10L,action(),hrId);
        assertThrows(KpiVersionConflictException.class,()->service.overrideAndApprove(10L,new AnnualAppraisalActionRequest(staleReview,AnnualAppraisalRecommendation.BOTH,"Reason"),hrId));
    }
    @Test void unrelatedSuperiorsEmployeesAndLegacyAuthoritiesCannotWrite() {
        auth(outsiderId,"CAN_SUBMIT_ANNUAL_APPRAISAL");assertThrows(AccessDeniedException.class,()->service.create(request(null,null),outsiderId));
        auth(employeeId,"ROLE_USER");assertThrows(AccessDeniedException.class,()->service.create(request(null,null),employeeId));
        auth(superiorId,"CAN_MANAGE_EVALUATION","CAN_MANAGE_EVALUATION_CYCLE");assertThrows(AccessDeniedException.class,()->service.create(request(null,null),superiorId));
        verify(appraisals,never()).saveAndFlush(any());
    }
    @Test void reportingChangesFreezeWritesForOldAndNewSuperiorsAndHr() {
        submitted();employee.setManager(actor(outsiderId));hr();assertFalse(service.get(10L,hrId).getAllowedActions().contains("APPROVE"));
        assertThrows(KpiVersionConflictException.class,()->service.approve(10L,action(),hrId));
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");assertThrows(AccessDeniedException.class,()->service.update(10L,request(null,null),superiorId));
        auth(outsiderId,"CAN_SUBMIT_ANNUAL_APPRAISAL");assertThrows(AccessDeniedException.class,()->service.update(10L,request(null,null),outsiderId));
    }
    @Test void combinedPermissionsDoNotAllowSelfApproval() {
        submitted();auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL","CAN_REVIEW_ANNUAL_APPRAISAL");
        assertThrows(AccessDeniedException.class,()->service.approve(10L,action(),superiorId));
        assertThrows(AccessDeniedException.class,()->service.returnForRevision(10L,new AnnualAppraisalActionRequest(appraisal.getLockVersion(),null,"Reason"),superiorId));
    }
    @Test void hrAuthorityDoesNotAllowAnEmployeeToReviewTheirOwnAppraisal() {
        submitted();auth(employeeId,"CAN_REVIEW_ANNUAL_APPRAISAL");
        assertThrows(AccessDeniedException.class,()->service.approve(10L,action(),employeeId));
        assertTrue(service.get(10L,employeeId).getAllowedActions().isEmpty());
    }
    @ParameterizedTest @EnumSource(value=AnnualKpiReviewPeriodStatus.class,names={"UPCOMING","CLOSED"})
    void upcomingAndClosedAreReadOnly(AnnualKpiReviewPeriodStatus status) {
        submitted();period.setStatus(status);hr();assertTrue(service.get(10L,hrId).getAllowedActions().isEmpty());
        assertThrows(BadRequestException.class,()->service.approve(10L,action(),hrId));
        auth(superiorId,"CAN_SUBMIT_ANNUAL_APPRAISAL");assertThrows(BadRequestException.class,()->service.update(10L,request(null,null),superiorId));
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
    @Test void hrCannotReadPrivateDraftAndEmployeesCannotReadAnyAppraisal() {
        service.create(request(null,null),superiorId);hr();assertThrows(AccessDeniedException.class,()->service.get(10L,hrId));
        auth(employeeId,"ROLE_USER");assertThrows(AccessDeniedException.class,()->service.get(10L,employeeId));
    }
    @Test void readingReadinessAndTeamDoesNotCreateDrafts() {
        assertTrue(service.readiness(7L,superiorId).ready());assertEquals(1,service.team(1L,superiorId).size());
        verify(appraisals,never()).saveAndFlush(any());verify(activity,never()).save(any());
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
