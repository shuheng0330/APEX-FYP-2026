package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.*;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndividualKpiPlanServiceTest {
    KpiPlanRepository plans=mock(KpiPlanRepository.class);
    AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    ReviewPeriodParticipantRepository participants=mock(ReviewPeriodParticipantRepository.class);
    StaffRepository staff=mock(StaffRepository.class);
    KpiAssignmentService assignments=mock(KpiAssignmentService.class);
    Clock clock=Clock.fixed(Instant.parse("2027-06-01T00:00:00Z"),ZoneOffset.UTC);
    KpiPlanServiceImpl service=new KpiPlanServiceImpl(plans,periods,Mappers.getMapper(KpiPlanMapper.class),
            new KpiPlanValidator(),clock,assignments,staff,participants,mock(OrgChartRepository.class),
            mock(PerformanceDepartmentResolver.class));
    UUID employeeId=UUID.randomUUID(),superiorId=UUID.randomUUID(),outsiderId=UUID.randomUUID();
    Staff employee,superior,outsider; ReviewPeriodParticipant participant; AnnualKpiReviewPeriod period;
    KpiPlan plan; KpiPlanRequest request;

    @BeforeEach void setup() {
        superior=new Staff();superior.setId(superiorId);superior.setName("Sales Manager");
        superior.setAccountStatus(StaffAccountStatus.ACTIVE);
        employee=new Staff();employee.setId(employeeId);employee.setName("Amir");
        employee.setAccountStatus(StaffAccountStatus.ACTIVE);employee.setManager(superior);
        outsider=new Staff();outsider.setId(outsiderId);outsider.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findById(employeeId)).thenReturn(Optional.of(employee));
        when(staff.findById(superiorId)).thenReturn(Optional.of(superior));
        when(staff.findById(outsiderId)).thenReturn(Optional.of(outsider));
        period=new AnnualKpiReviewPeriod();period.setId(1L);period.setName("2027 Annual KPI Review");
        period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        period.setParticipantsSnapshottedAt(OffsetDateTime.now(clock));
        period.setKpiSetupDeadline(LocalDate.of(2027,1,20));
        participant=new ReviewPeriodParticipant();participant.setId(7L);participant.setStaff(employee);
        participant.setStaffName("Amir");participant.setReviewPeriod(period);
        when(participants.findByReviewPeriodIdAndStaffId(1L,employeeId)).thenReturn(Optional.of(participant));
        plan=new KpiPlan();plan.setId(10L);plan.setReviewPeriod(period);plan.setLevel(KpiLevel.INDIVIDUAL);
        plan.setOwnerParticipantId(7L);plan.setOwnerParticipant(participant);plan.setStatus(KpiPlanStatus.DRAFT);
        when(plans.lockById(10L)).thenReturn(Optional.of(plan));when(plans.findById(10L)).thenReturn(Optional.of(plan));
        when(plans.saveAndFlush(any())).thenAnswer(i->{KpiPlan p=i.getArgument(0);if(p.getId()==null)p.setId(10L);return p;});
        request=new KpiPlanRequest();request.setReviewPeriodId(1L);request.setItems(List.of());
    }
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();}
    void complete() {
        var item=new Kpi();item.setId(11L);
        Mappers.getMapper(KpiPlanMapper.class).update(KpiPlanValidatorTest.item("New Customers","100"),item);
        plan.getItems().add(item);
    }
    void pending() {complete();service.submitIndividual(10L,employeeId);}
    KpiPlanReturnRequest reason(String value) {var request=new KpiPlanReturnRequest();request.setReason(value);return request;}
    void reviewerContext() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(superiorId,null,
                List.of(new SimpleGrantedAuthority("CAN_REVIEW_INDIVIDUAL_KPI"))));
    }

    @Test void ownerCanCreateIncompleteLateDraftButCannotChooseAnotherEmployee() {
        var created=service.createIndividual(request,employeeId);
        assertEquals(KpiPlanStatus.DRAFT,created.getStatus());assertTrue(created.isOverdue());
        assertEquals(7L,created.getOwnerParticipantId());assertEquals("Amir",created.getEmployeeName());
        request.setOwnerParticipantId(8L);
        assertThrows(AccessDeniedException.class,()->service.createIndividual(request,employeeId));
    }
    @Test void unenrolledOrDuplicateOwnerCannotCreatePlan() {
        assertThrows(AccessDeniedException.class,()->service.createIndividual(request,outsiderId));
        when(plans.findByLevelAndOwnerParticipantId(KpiLevel.INDIVIDUAL,7L)).thenReturn(Optional.of(plan));
        assertThrows(BadRequestException.class,()->service.createIndividual(request,employeeId));
    }
    @Test void ownerCanEditButCannotChangeScopeOrAnotherEmployeesPlan() {
        request.setItems(List.of(KpiPlanValidatorTest.item("Sales","100")));
        assertEquals(1,service.updateIndividual(10L,request,employeeId).getItems().size());
        request.setOwnerParticipantId(8L);
        assertThrows(BadRequestException.class,()->service.updateIndividual(10L,request,employeeId));
        assertThrows(AccessDeniedException.class,()->service.updateIndividual(10L,request,outsiderId));
    }
    @Test void submissionRequiresCompletePlanAndActiveImmediateSuperior() {
        assertThrows(BadRequestException.class,()->service.submitIndividual(10L,employeeId));
        complete();employee.setManager(null);
        assertThrows(BadRequestException.class,()->service.submitIndividual(10L,employeeId));
        employee.setManager(superior);superior.setAccountStatus(null);
        assertThrows(BadRequestException.class,()->service.submitIndividual(10L,employeeId));
        assertEquals(KpiPlanStatus.DRAFT,plan.getStatus());verify(assignments,never()).cascade(any());
    }
    @Test void submissionRoutesWholePlanAndDoesNotAssignUntilApproval() {
        pending();
        assertEquals(KpiPlanStatus.PENDING_APPROVAL,plan.getStatus());
        assertEquals(superiorId,plan.getSubmittedToSuperiorId());
        assertEquals("Sales Manager",service.individualPlan(10L,employeeId).getSubmittedToSuperiorName());
        assertEquals(employeeId,plan.getSubmittedBy());assertTrue(plan.getSubmittedLate());
        assertThrows(BadRequestException.class,()->service.updateIndividual(10L,request,employeeId));
        assertThrows(BadRequestException.class,()->service.submitIndividual(10L,employeeId));
        verify(assignments,never()).cascade(any());
    }
    @Test void pendingQueueAndDetailsAreScopedToRoutedSuperior() {
        pending();reviewerContext();
        when(plans.findAllByLevelAndStatusAndSubmittedToSuperiorIdOrderBySubmittedAtAscIdAsc(
                KpiLevel.INDIVIDUAL,KpiPlanStatus.PENDING_APPROVAL,superiorId)).thenReturn(List.of(plan));
        assertEquals(1,service.pendingIndividualPlans(superiorId).size());
        assertEquals(10L,service.individualPlan(10L,superiorId).getId());
        assertThrows(AccessDeniedException.class,()->service.individualPlan(10L,outsiderId));
    }
    @Test void onlyRoutedCurrentSuperiorMayDecide() {
        pending();
        assertThrows(AccessDeniedException.class,()->service.approveIndividual(10L,outsiderId));
        employee.setManager(outsider);
        assertThrows(AccessDeniedException.class,()->service.approveIndividual(10L,superiorId));
        assertThrows(AccessDeniedException.class,()->service.returnIndividual(10L,reason("Revise"),superiorId));
        assertEquals(KpiPlanStatus.PENDING_APPROVAL,plan.getStatus());
        reviewerContext();
        assertThrows(AccessDeniedException.class,()->service.individualPlan(10L,superiorId));
    }
    @Test void reviewHistoryUsesRoutedSuperiorScopeAndSnapshotDepartment() {
        pending();participant.setDepartmentName("Retail Sales");
        when(plans.findIndividualReviewPlans(eq(KpiLevel.INDIVIDUAL),anyCollection(),eq(superiorId))).thenReturn(List.of(plan));
        var queue=service.individualReviewPlans(superiorId);
        assertEquals(1,queue.size());assertEquals("Retail Sales",queue.get(0).getDepartmentName());
        verify(plans).findIndividualReviewPlans(KpiLevel.INDIVIDUAL,
                List.of(KpiPlanStatus.PENDING_APPROVAL,KpiPlanStatus.APPROVED,KpiPlanStatus.RETURNED),superiorId);
    }
    @Test void returnRequiresReasonAndOwnerCanReviseThenResubmit() {
        pending();
        for(var invalid:Arrays.asList(null,"","  ","x".repeat(10001)))
            assertThrows(BadRequestException.class,()->service.returnIndividual(10L,reason(invalid),superiorId));
        assertEquals(KpiPlanStatus.RETURNED,service.returnIndividual(10L,reason(" Clarify target "),superiorId).getStatus());
        assertEquals("Clarify target",plan.getReturnReason());verify(assignments,never()).cascade(any());
        request.setItems(List.of(KpiPlanValidatorTest.item("Updated target","100")));
        assertEquals("Clarify target",service.updateIndividual(10L,request,employeeId).getReturnReason());
        assertEquals(KpiPlanStatus.PENDING_APPROVAL,service.submitIndividual(10L,employeeId).getStatus());
        assertNull(plan.getReturnReason());assertNull(plan.getReviewedBy());
    }
    @Test void approvalAssignsOnlyOwnerPlanAndBecomesImmutable() {
        pending();
        assertEquals(KpiPlanStatus.APPROVED,service.approveIndividual(10L,superiorId).getStatus());
        verify(assignments).requirePublishedRoster(period);verify(assignments).cascade(plan);
        assertThrows(BadRequestException.class,()->service.approveIndividual(10L,superiorId));
        assertThrows(BadRequestException.class,()->service.updateIndividual(10L,request,employeeId));
        verify(assignments,times(1)).cascade(plan);
    }
    @Test void closedPeriodBlocksMutationsButOwnerCanReadHistory() {
        pending();period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertThrows(BadRequestException.class,()->service.approveIndividual(10L,superiorId));
        assertThrows(BadRequestException.class,()->service.returnIndividual(10L,reason("Revise"),superiorId));
        assertThrows(BadRequestException.class,()->service.updateIndividual(10L,request,employeeId));
        assertThrows(BadRequestException.class,()->service.createIndividual(request,employeeId));
        assertEquals(AnnualKpiReviewPeriodStatus.CLOSED,service.individualPlan(10L,employeeId).getReviewPeriodStatus());
    }
    @Test void assignedPlansRequireOwnerParticipation() {
        when(plans.findAssignedPlans(7L,1L)).thenReturn(List.of(plan));
        assertEquals(1,service.myAssignedPlans(1L,employeeId).size());
        assertThrows(AccessDeniedException.class,()->service.myAssignedPlans(1L,outsiderId));
    }
    @Test void employeePeriodsExposeRecordedLevelAllocationNotCurrentRole() {
        var level=new EmployeeLevel();level.setId(4L);level.setName("Executive");level.setCode("EXECUTIVE");
        var allocation=new ReviewPeriodEmployeeLevelConfiguration();allocation.setId(40L);allocation.setEmployeeLevel(level);
        allocation.setReviewPeriod(period);allocation.setCompanyKpiWeight(new java.math.BigDecimal("15.00"));
        allocation.setDepartmentKpiWeight(new java.math.BigDecimal("25.00"));
        allocation.setIndividualKpiWeight(new java.math.BigDecimal("60.00"));
        participant.setEmployeeLevelConfiguration(allocation);
        var newLevel=new EmployeeLevel();newLevel.setId(6L);newLevel.setName("General");
        var currentRole=new Role();currentRole.setEmployeeLevel(newLevel);employee.setRole(currentRole);
        var earlierPeriod=new AnnualKpiReviewPeriod();earlierPeriod.setId(2L);
        var earlierParticipant=new ReviewPeriodParticipant();earlierParticipant.setReviewPeriod(earlierPeriod);
        var earlierAllocation=new ReviewPeriodEmployeeLevelConfiguration();earlierAllocation.setEmployeeLevel(level);
        earlierAllocation.setIndividualKpiWeight(new java.math.BigDecimal("55.50"));
        earlierParticipant.setEmployeeLevelConfiguration(earlierAllocation);
        when(participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(employeeId)).thenReturn(List.of(participant,earlierParticipant));
        var contexts=service.individualPeriods(employeeId);
        assertEquals(1L,contexts.get(0).getId());
        assertEquals("Executive",contexts.get(0).getKpiAllocation().getEmployeeLevelName());
        assertEquals(new java.math.BigDecimal("15.00"),contexts.get(0).getKpiAllocation().getCompanyKpiWeight());
        assertEquals(new java.math.BigDecimal("25.00"),contexts.get(0).getKpiAllocation().getDepartmentKpiWeight());
        assertEquals(new java.math.BigDecimal("60.00"),contexts.get(0).getKpiAllocation().getIndividualKpiWeight());
        assertEquals(new java.math.BigDecimal("55.50"),contexts.get(1).getKpiAllocation().getIndividualKpiWeight());
    }
    @Test void missingHistoricalAllocationDoesNotInventDefaults() {
        when(participants.findAllByStaffIdOrderByReviewPeriodStartDateDesc(employeeId)).thenReturn(List.of(participant));
        assertNull(service.individualPeriods(employeeId).get(0).getKpiAllocation());
    }
}
