package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndividualKpiAssistanceServiceTest {
    KpiPlanRepository plans=mock(KpiPlanRepository.class);
    AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    IndividualKpiAssistanceAuthorizationRepository assistance=mock(IndividualKpiAssistanceAuthorizationRepository.class);
    StaffRepository staff=mock(StaffRepository.class);
    KpiAssignmentService assignments=mock(KpiAssignmentService.class);
    Clock clock=Clock.fixed(Instant.parse("2027-06-01T00:00:00Z"),ZoneOffset.UTC);
    KpiPlanServiceImpl service=new KpiPlanServiceImpl(plans,periods,Mappers.getMapper(KpiPlanMapper.class),
        new KpiPlanValidator(),clock,assignments,staff,participants,mock(OrgChartRepository.class),
        mock(PerformanceDepartmentResolver.class),assistance,Mappers.getMapper(KpiAssistanceMapper.class));
    UUID employeeId=UUID.randomUUID(),superiorId=UUID.randomUUID(),hrId=UUID.randomUUID(),outsiderId=UUID.randomUUID();
    Staff employee,superior,hr,outsider;ReviewPeriodParticipant participant;AnnualKpiReviewPeriod period;
    IndividualKpiAssistanceAuthorization authorization;KpiPlan createdPlan;

    @BeforeEach void setup() {
        employee=actor(employeeId,"Amir");superior=actor(superiorId,"Immediate Superior");
        hr=actor(hrId,"HR");outsider=actor(outsiderId,"Other Superior");employee.setManager(superior);
        period=new AnnualKpiReviewPeriod();period.setId(1L);period.setName("2027 Annual Review");
        period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);period.setParticipantsSnapshottedAt(OffsetDateTime.now(clock));
        period.setKpiSetupDeadline(LocalDate.of(2027,1,15));
        participant=new ReviewPeriodParticipant();participant.setId(7L);participant.setStaff(employee);
        participant.setStaffName("Amir");participant.setDepartmentName("Retail Sales");participant.setReviewPeriod(period);
        when(participants.findById(7L)).thenReturn(Optional.of(participant));
        when(participants.findByReviewPeriodIdAndStaffId(1L,employeeId)).thenReturn(Optional.of(participant));
        authorization=new IndividualKpiAssistanceAuthorization();authorization.setId(8L);
        authorization.setOwnerParticipant(participant);authorization.setSuperior(superior);
        authorization.setRequestedAt(OffsetDateTime.now(clock));
        when(assistance.lockById(8L)).thenReturn(Optional.of(authorization));
        when(assistance.findById(8L)).thenReturn(Optional.of(authorization));
        when(assistance.saveAndFlush(any())).thenAnswer(call->{var value=(IndividualKpiAssistanceAuthorization)call.getArgument(0);
            if(value.getId()==null)value.setId(8L);return value;});
        when(plans.saveAndFlush(any())).thenAnswer(call->{createdPlan=call.getArgument(0);if(createdPlan.getId()==null)createdPlan.setId(10L);
            for(int i=0;i<createdPlan.getItems().size();i++)createdPlan.getItems().get(i).setId(100L+i);
            when(plans.findByAssistanceAuthorizationId(8L)).thenReturn(Optional.of(createdPlan));
            when(plans.lockById(10L)).thenReturn(Optional.of(createdPlan));
            when(plans.findById(10L)).thenReturn(Optional.of(createdPlan));
            when(plans.findByLevelAndOwnerParticipantId(KpiLevel.INDIVIDUAL,7L)).thenReturn(Optional.of(createdPlan));
            return createdPlan;});
    }
    Staff actor(UUID id,String name) {
        var value=new Staff();value.setId(id);value.setName(name);value.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findById(id)).thenReturn(Optional.of(value));return value;
    }
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();}
    KpiAssistanceRequest request() {var request=new KpiAssistanceRequest();request.setOwnerParticipantId(7L);request.setRequestReason(" Help preparing measurable KPIs ");return request;}
    AssistedIndividualKpiPlanRequest items(String weight) {
        var request=new AssistedIndividualKpiPlanRequest();request.setItems(List.of(KpiPlanValidatorTest.item("New Customers",weight)));return request;
    }
    void authorized() {service.authorizeAssistance(8L,hrId);}
    void created() {authorized();service.createAssistedIndividual(8L,items("100"),superiorId);}
    void security(UUID id,String permission) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority(permission))));
    }
    @Test void requestScopesToCurrentSubordinateAndPublishedParticipant() {
        var dto=service.requestAssistance(request(),superiorId);
        assertEquals(KpiAssistanceStatus.REQUESTED,dto.getStatus());assertEquals(superiorId,dto.getSuperiorId());
        assertEquals(employeeId,dto.getEmployeeId());assertEquals(1L,dto.getReviewPeriodId());
        assertEquals("Help preparing measurable KPIs",dto.getRequestReason());
        assertNull(dto.getAuthorizedAt());assertNull(dto.getPlanId());verify(plans,never()).saveAndFlush(any());
        verify(assignments).requirePublishedRoster(period);
    }
    @Test void newRequestsRequireANonblankReasonWithinTheLimit() {
        for(var reason:Arrays.asList(null,"","  ","\t\n","x".repeat(10001))) {
            var request=request();request.setRequestReason(reason);
            assertThrows(BadRequestException.class,()->service.requestAssistance(request,superiorId));
        }
        verify(assistance,never()).saveAndFlush(any());
        var request=request();request.setRequestReason("x".repeat(10000));
        assertEquals(10000,service.requestAssistance(request,superiorId).getRequestReason().length());
    }
    @Test void earlierRequestsRemainReadableAndReviewableWithoutAnInventedReason() {
        assertNull(service.assistanceCase(8L,superiorId).getRequestReason());
        assertNull(service.authorizeAssistance(8L,hrId).getRequestReason());
        assertEquals(KpiAssistanceStatus.AUTHORIZED,authorization.getStatus());
    }
    @Test void unrelatedEmployeeSelfAndUnknownParticipantsAreDenied() {
        assertThrows(AccessDeniedException.class,()->service.requestAssistance(request(),outsiderId));
        employee.setManager(employee);assertThrows(AccessDeniedException.class,()->service.requestAssistance(request(),employeeId));
        var request=request();request.setOwnerParticipantId(9L);
        assertThrows(BadRequestException.class,()->service.requestAssistance(request,superiorId));
        assertThrows(BadRequestException.class,()->service.requestAssistance(null,superiorId));
        verify(assistance,never()).saveAndFlush(any());
    }
    @Test void duplicateRequestsAndExistingPlansAreNotOverwritten() {
        when(assistance.existsByOwnerParticipantIdAndSuperiorIdAndStatusNot(7L,superiorId,KpiAssistanceStatus.REJECTED)).thenReturn(true);
        assertThrows(BadRequestException.class,()->service.requestAssistance(request(),superiorId));
        when(assistance.existsByOwnerParticipantIdAndSuperiorIdAndStatusNot(7L,superiorId,KpiAssistanceStatus.REJECTED)).thenReturn(false);
        when(plans.findByLevelAndOwnerParticipantId(KpiLevel.INDIVIDUAL,7L)).thenReturn(Optional.of(new KpiPlan()));
        assertThrows(BadRequestException.class,()->service.requestAssistance(request(),superiorId));
        assertThrows(BadRequestException.class,()->service.authorizeAssistance(8L,hrId));
        authorization.setStatus(KpiAssistanceStatus.AUTHORIZED);
        assertThrows(BadRequestException.class,()->service.createAssistedIndividual(8L,items("100"),superiorId));
        verify(plans,never()).saveAndFlush(any());
    }
    @Test void hrAuthorizationRechecksRelationshipAndHasNoPostCreationApproval() {
        employee.setManager(outsider);assertThrows(AccessDeniedException.class,()->service.authorizeAssistance(8L,hrId));
        employee.setManager(superior);var result=service.authorizeAssistance(8L,hrId);
        assertEquals(KpiAssistanceStatus.AUTHORIZED,result.getStatus());assertEquals(hrId,result.getAuthorizedById());
        assertNotNull(result.getAuthorizedAt());assertNull(result.getConsumedAt());
        assertThrows(BadRequestException.class,()->service.authorizeAssistance(8L,hrId));
    }
    @ParameterizedTest @ValueSource(strings={"create","update","confirm"})
    void caseConsentAndRequestingSuperiorAreRequiredAtEveryWrite(String action) {
        assertThrows(BadRequestException.class,()->perform(action,superiorId));
        authorized();assertThrows(AccessDeniedException.class,()->perform(action,outsiderId));
        employee.setManager(outsider);assertThrows(AccessDeniedException.class,()->perform(action,superiorId));
        verify(plans,never()).saveAndFlush(any());verify(assignments,never()).cascade(any());
    }
    void perform(String action,UUID actor) {
        switch(action) {
            case "create" -> service.createAssistedIndividual(8L,items("100"),actor);
            case "update" -> service.updateAssistedIndividual(8L,items("100"),actor);
            default -> service.confirmAssistedIndividual(8L,actor);
        }
    }
    @Test void missingConsentCannotCreateOrReadAnArbitraryPlan() {
        assertThrows(BadRequestException.class,()->service.createAssistedIndividual(9L,items("100"),superiorId));
        assertThrows(BadRequestException.class,()->service.assistedIndividualPlan(8L,superiorId));
        assertThrows(AccessDeniedException.class,()->service.assistedIndividualPlan(8L,outsiderId));
    }
    @Test void authorizedDraftMayBeIncompleteButConfirmationRequiresCompleteHundredPercent() {
        authorized();var draft=service.createAssistedIndividual(8L,items("60"),superiorId);
        assertEquals(KpiPlanStatus.DRAFT,draft.getStatus());assertEquals(8L,draft.getAssistanceAuthorizationId());
        assertTrue(draft.isOverdue());assertEquals(superiorId,draft.getCreatedBy());assertEquals(7L,draft.getOwnerParticipantId());
        assertThrows(BadRequestException.class,()->service.confirmAssistedIndividual(8L,superiorId));
        var incomplete=items("100");incomplete.getItems().get(0).getScoringDefinitions().remove(5);
        service.updateAssistedIndividual(8L,incomplete,superiorId);
        assertThrows(BadRequestException.class,()->service.confirmAssistedIndividual(8L,superiorId));
        assertEquals(KpiAssistanceStatus.AUTHORIZED,authorization.getStatus());verify(assignments,never()).cascade(any());
    }
    @Test void directConfirmationConsumesConsentAndAssignsTheOwnerWithoutSubmission() {
        created();var confirmed=service.confirmAssistedIndividual(8L,superiorId);
        assertEquals(KpiPlanStatus.APPROVED,confirmed.getStatus());assertNull(confirmed.getSubmittedAt());
        assertNull(confirmed.getSubmittedToSuperiorId());assertEquals(superiorId,confirmed.getReviewedBy());
        assertTrue(confirmed.getReviewedLate());assertEquals(KpiAssistanceStatus.CONSUMED,authorization.getStatus());
        assertNotNull(authorization.getConsumedAt());verify(assignments,times(1)).cascade(createdPlan);
        assertThrows(BadRequestException.class,()->service.confirmAssistedIndividual(8L,superiorId));
        assertThrows(BadRequestException.class,()->service.updateAssistedIndividual(8L,items("100"),superiorId));
        assertThrows(BadRequestException.class,()->service.createAssistedIndividual(8L,items("100"),superiorId));
        assertEquals(KpiPlanStatus.APPROVED,service.assistedIndividualPlan(8L,superiorId).getStatus());
    }
    @Test void ownerCannotTakeOverAssistedDraftThroughNormalEndpoints() {
        created();var ordinary=new KpiPlanRequest();ordinary.setReviewPeriodId(1L);ordinary.setItems(List.of());
        assertThrows(BadRequestException.class,()->service.updateIndividual(10L,ordinary,employeeId));
        assertThrows(BadRequestException.class,()->service.submitIndividual(10L,employeeId));
        assertThrows(BadRequestException.class,()->service.createIndividual(ordinary,employeeId));
        assertEquals(KpiPlanStatus.DRAFT,service.individualPlan(10L,employeeId).getStatus());
    }
    @Test void changedRelationshipAfterDraftCreationPreventsUpdateAndConfirmation() {
        created();employee.setManager(outsider);
        assertThrows(AccessDeniedException.class,()->service.updateAssistedIndividual(8L,items("100"),superiorId));
        assertThrows(AccessDeniedException.class,()->service.confirmAssistedIndividual(8L,superiorId));
        assertEquals(KpiAssistanceStatus.AUTHORIZED,authorization.getStatus());verify(assignments,never()).cascade(any());
    }
    @Test void closedPeriodsBlockAllActionsButDoNotHideHistory() {
        created();period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertThrows(BadRequestException.class,()->service.requestAssistance(request(),superiorId));
        assertThrows(BadRequestException.class,()->service.updateAssistedIndividual(8L,items("100"),superiorId));
        assertThrows(BadRequestException.class,()->service.confirmAssistedIndividual(8L,superiorId));
        authorization.setStatus(KpiAssistanceStatus.REQUESTED);
        assertThrows(BadRequestException.class,()->service.authorizeAssistance(8L,hrId));
        assertEquals(KpiPlanStatus.DRAFT,service.assistedIndividualPlan(8L,superiorId).getStatus());
    }
    @ParameterizedTest @ValueSource(strings={"employee","superior","hr"})
    void inactiveOrDeletedActorsCannotPerformAssistance(String actor) {
        if(actor.equals("hr")) {hr.setDeleted(true);assertThrows(AccessDeniedException.class,()->service.authorizeAssistance(8L,hrId));}
        else {
            (actor.equals("employee")?employee:superior).setAccountStatus(StaffAccountStatus.INACTIVE);
            assertThrows(AccessDeniedException.class,()->service.requestAssistance(request(),superiorId));
        }
    }
    @Test void hrListsAllCasesButSuperiorReadsOnlyScopedCases() {
        security(superiorId,"CAN_REVIEW_INDIVIDUAL_KPI");
        when(assistance.findCurrentSuperiorCases(superiorId)).thenReturn(List.of(authorization));
        assertEquals(1,service.assistanceCases(superiorId).size());verify(assistance,never()).findAllByOrderByRequestedAtDescIdDesc();
        assertEquals(8L,service.assistanceCase(8L,superiorId).getId());
        assertThrows(AccessDeniedException.class,()->service.assistanceCase(8L,outsiderId));
        security(hrId,"CAN_AUTHORIZE_INDIVIDUAL_KPI_ASSISTANCE");
        when(assistance.findAllByOrderByRequestedAtDescIdDesc()).thenReturn(List.of(authorization));
        assertEquals(1,service.assistanceCases(hrId).size());assertEquals(8L,service.assistanceCase(8L,hrId).getId());
    }
    @Test void employeeOptionsExcludeExistingPlansAndUseLiveSubordinatesNotSnapshotSuperior() {
        participant.setSuperior(outsider);
        when(participants.findCurrentSubordinateParticipants(superiorId)).thenReturn(List.of(participant));
        assertEquals(employeeId,service.assistanceEmployees(superiorId).get(0).getEmployeeId());
        when(plans.findByLevelAndOwnerParticipantId(KpiLevel.INDIVIDUAL,7L)).thenReturn(Optional.of(new KpiPlan()));
        assertTrue(service.assistanceEmployees(superiorId).isEmpty());
    }
    @Test void assistedUpdatesCannotReuseItemIdsFromAnotherPlan() {
        created();var request=items("100");request.getItems().get(0).setId(999L);
        assertThrows(BadRequestException.class,()->service.updateAssistedIndividual(8L,request,superiorId));
    }
    @Test void rejectionPreservesHistoryAndAllowsAFreshEligibleRequest() {
        var reason=new KpiPlanReturnRequest();reason.setReason(" Please discuss the targets first ");
        var result=service.rejectAssistance(8L,reason,hrId);
        assertEquals(KpiAssistanceStatus.REJECTED,result.getStatus());assertEquals("Please discuss the targets first",result.getRejectionReason());
        assertEquals(hrId,result.getRejectedById());assertNotNull(result.getRejectedAt());assertNull(result.getAuthorizedAt());
        assertEquals("Please discuss the targets first",service.assistanceCase(8L,superiorId).getRejectionReason());
        assertThrows(BadRequestException.class,()->service.authorizeAssistance(8L,hrId));
        for(var action:List.of("create","update","confirm")) assertThrows(BadRequestException.class,()->perform(action,superiorId));
        var fresh=service.requestAssistance(request(),superiorId);
        assertEquals(KpiAssistanceStatus.REQUESTED,fresh.getStatus());assertNull(fresh.getRejectionReason());
        assertEquals(KpiAssistanceStatus.REJECTED,authorization.getStatus());
        verify(assistance).existsByOwnerParticipantIdAndSuperiorIdAndStatusNot(7L,superiorId,KpiAssistanceStatus.REJECTED);
        verify(assistance,atLeastOnce()).lockById(8L);
    }
    @Test void rejectionRequiresReasonAndOnlyOneHrDecisionCanSucceed() {
        for(var value:Arrays.asList(null,"", "   ","x".repeat(10001))) {
            var reason=new KpiPlanReturnRequest();reason.setReason(value);
            assertThrows(BadRequestException.class,()->service.rejectAssistance(8L,reason,hrId));
        }
        assertThrows(BadRequestException.class,()->service.rejectAssistance(8L,null,hrId));
        var reason=new KpiPlanReturnRequest();reason.setReason("Not required");
        authorized();assertThrows(BadRequestException.class,()->service.rejectAssistance(8L,reason,hrId));
        assertNull(authorization.getRejectedAt());
    }
    @Test void rejectedRequestDoesNotBypassNewRequestEligibilityOrDecisionScope() {
        var reason=new KpiPlanReturnRequest();reason.setReason("Review requirements");
        employee.setManager(outsider);assertThrows(AccessDeniedException.class,()->service.rejectAssistance(8L,reason,hrId));
        employee.setManager(superior);period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertThrows(BadRequestException.class,()->service.rejectAssistance(8L,reason,hrId));
        period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);service.rejectAssistance(8L,reason,hrId);
        assertThrows(BadRequestException.class,()->service.rejectAssistance(8L,reason,hrId));
        employee.setManager(outsider);assertThrows(AccessDeniedException.class,()->service.requestAssistance(request(),superiorId));
        employee.setManager(superior);when(plans.findByLevelAndOwnerParticipantId(KpiLevel.INDIVIDUAL,7L)).thenReturn(Optional.of(new KpiPlan()));
        assertThrows(BadRequestException.class,()->service.requestAssistance(request(),superiorId));
    }
}
