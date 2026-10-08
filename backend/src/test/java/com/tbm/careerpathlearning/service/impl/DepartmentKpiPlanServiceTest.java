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

class DepartmentKpiPlanServiceTest {
    KpiPlanRepository plans=mock(KpiPlanRepository.class);
    AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    StaffRepository staff=mock(StaffRepository.class);
    OrgChartRepository departments=mock(OrgChartRepository.class);
    PerformanceDepartmentResolver resolver=mock(PerformanceDepartmentResolver.class);
    KpiAssignmentService assignments=mock(KpiAssignmentService.class);
    Clock clock=Clock.fixed(Instant.parse("2027-06-01T00:00:00Z"),ZoneOffset.UTC);
    KpiPlanServiceImpl service=new KpiPlanServiceImpl(plans,periods,Mappers.getMapper(KpiPlanMapper.class),
        new KpiPlanValidator(),clock,assignments,staff,mock(ReviewPeriodParticipantRepository.class),departments,resolver);
    UUID actor=UUID.randomUUID();
    Staff hod; Role role; OrgChart department; AnnualKpiReviewPeriod period; KpiPlan plan; KpiPlanRequest request;

    @BeforeEach void setup() {
        department=new OrgChart();department.setId(2L);department.setType(OrgChartType.D);department.setName("Retail Sales");
        role=new Role();role.setId(3L);role.setOrgChart(department);
        hod=new Staff();hod.setId(actor);hod.setRole(role);hod.setAccountStatus(StaffAccountStatus.ACTIVE);
        when(staff.findById(actor)).thenReturn(Optional.of(hod));when(resolver.resolve(role)).thenReturn(department);
        period=new AnnualKpiReviewPeriod();period.setId(1L);period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        period.setKpiSetupDeadline(LocalDate.of(2027,1,20));period.setParticipantsSnapshottedAt(OffsetDateTime.now(clock));
        when(periods.findById(1L)).thenReturn(Optional.of(period));
        plan=new KpiPlan();plan.setId(10L);plan.setReviewPeriod(period);plan.setLevel(KpiLevel.DEPARTMENT);plan.setDepartment(department);
        when(plans.lockById(10L)).thenReturn(Optional.of(plan));when(plans.findById(10L)).thenReturn(Optional.of(plan));
        when(plans.saveAndFlush(any())).thenAnswer(i->{KpiPlan p=i.getArgument(0);if(p.getId()==null)p.setId(10L);return p;});
        request=new KpiPlanRequest();request.setReviewPeriodId(1L);request.setDepartmentId(2L);request.setItems(List.of());
    }
    @AfterEach void cleanup() {SecurityContextHolder.clearContext();}
    void complete() {
        var item=new Kpi();item.setId(11L);
        Mappers.getMapper(KpiPlanMapper.class).update(KpiPlanValidatorTest.item("Sales","100"),item);
        plan.getItems().add(item);
    }
    KpiPlanReturnRequest reason(String value) {var r=new KpiPlanReturnRequest();r.setReason(value);return r;}
    void pending() {complete();service.submitDepartment(10L,actor);}

    @Test void createsIncompleteLateDraftWithManualKra() {
        var item=new KpiItemDto();item.setKra("Customer retention");request.setItems(List.of(item));
        var dto=service.createDepartment(request,actor);
        assertEquals(KpiPlanStatus.DRAFT,dto.getStatus());assertTrue(dto.isOverdue());
        assertEquals("Customer retention",dto.getItems().get(0).getKra());verify(assignments,never()).cascade(any());
    }
    @Test void rejectsCrossDepartmentCreationAndRead() {
        request.setDepartmentId(9L);assertThrows(AccessDeniedException.class,()->service.createDepartment(request,actor));
        var other=new OrgChart();other.setId(9L);plan.setDepartment(other);
        assertThrows(AccessDeniedException.class,()->service.departmentPlan(10L,actor));
        verify(plans,never()).saveAndFlush(any());
    }
    @Test void hodListAndOptionsAreDepartmentScoped() {
        when(plans.findAllByLevelAndDepartmentIdOrderByUpdatedAtDesc(KpiLevel.DEPARTMENT,2L)).thenReturn(List.of(plan));
        assertEquals(1,service.departmentPlans(actor).size());assertEquals(2L,service.departmentOptions(actor).get(0).id());
        verify(plans,never()).findAllByLevelOrderByUpdatedAtDesc(any());verifyNoInteractions(departments);
    }
    @Test void mdReadCanSeeDepartmentQueueWithoutHodScope() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,
            List.of(new SimpleGrantedAuthority("CAN_APPROVE_DEPARTMENT_KPI"))));
        when(plans.findAllByLevelOrderByUpdatedAtDesc(KpiLevel.DEPARTMENT)).thenReturn(List.of(plan));
        when(departments.findAllByOrgChartTypeD()).thenReturn(List.of(department));
        when(plans.findAllByLevelAndStatusOrderBySubmittedAtAscIdAsc(KpiLevel.DEPARTMENT,KpiPlanStatus.PENDING_APPROVAL)).thenReturn(List.of());
        assertEquals(1,service.departmentPlans(actor).size());assertEquals(1,service.departmentOptions(actor).size());
        assertTrue(service.pendingDepartmentPlans(actor).isEmpty());verifyNoInteractions(resolver);
    }
    @Test void administrativeReviewerWithPermissionCanReadAndApproveWithoutReviewParticipation() {
        pending();clearInvocations(resolver);role.setPerformanceReviewEligible(false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,
            List.of(new SimpleGrantedAuthority("CAN_APPROVE_DEPARTMENT_KPI"))));
        when(plans.findAllByLevelOrderByUpdatedAtDesc(KpiLevel.DEPARTMENT)).thenReturn(List.of(plan));
        when(departments.findAllByOrgChartTypeD()).thenReturn(List.of(department));
        when(periods.findAllByOrderByStartDateDescIdDesc()).thenReturn(List.of(period));
        when(plans.findAllByLevelAndStatusOrderBySubmittedAtAscIdAsc(KpiLevel.DEPARTMENT,KpiPlanStatus.PENDING_APPROVAL))
            .thenReturn(List.of(plan));
        assertEquals(1,service.departmentPlans(actor).size());
        assertEquals(1,service.departmentOptions(actor).size());
        assertEquals(1,service.pendingDepartmentPlans(actor).size());
        assertEquals(1,service.departmentPeriods(actor).size());
        assertEquals(10L,service.departmentPlan(10L,actor).getId());
        assertEquals(KpiPlanStatus.APPROVED,service.approveDepartment(10L,actor).getStatus());
        verifyNoInteractions(resolver);
    }
    @Test void administrativeReviewerWithPermissionCanReturnWithoutReviewParticipation() {
        pending();role.setPerformanceReviewEligible(false);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,
            List.of(new SimpleGrantedAuthority("CAN_APPROVE_DEPARTMENT_KPI"))));
        assertEquals(KpiPlanStatus.RETURNED,service.returnDepartment(10L,reason("Clarify target"),actor).getStatus());
    }
    @Test void cannotCreateSecondPlanForSameDepartmentAndPeriod() {
        when(plans.findByReviewPeriodIdAndLevelAndDepartmentId(1L,KpiLevel.DEPARTMENT,2L)).thenReturn(Optional.of(plan));
        assertThrows(BadRequestException.class,()->service.createDepartment(request,actor));
    }
    @Test void rejectsEmployeeOwnerAndScopeChanges() {
        request.setOwnerParticipantId(5L);assertThrows(BadRequestException.class,()->service.createDepartment(request,actor));
        request.setOwnerParticipantId(null);request.setDepartmentId(9L);
        assertThrows(BadRequestException.class,()->service.updateDepartment(10L,request,actor));
    }
    @Test void foreignItemCannotBeInsertedIntoDraft() {
        var item=KpiPlanValidatorTest.item("Sales","100");item.setId(99L);request.setItems(List.of(item));
        assertThrows(BadRequestException.class,()->service.updateDepartment(10L,request,actor));
    }
    @Test void unclassifiedOrAmbiguousDepartmentIsNotGuessed() {
        when(resolver.resolve(role)).thenReturn(null);
        assertThrows(BadRequestException.class,()->service.createDepartment(request,actor));
        when(resolver.resolve(role)).thenThrow(new BadRequestException("Ambiguous Department"));
        assertThrows(BadRequestException.class,()->service.createDepartment(request,actor));
    }
    @Test void systemDeletedAndInactiveAccountsCannotManageOrReview() {
        role.setPerformanceReviewEligible(false);assertThrows(AccessDeniedException.class,()->service.createDepartment(request,actor));
        role.setPerformanceReviewEligible(true);hod.setDeleted(true);assertThrows(AccessDeniedException.class,()->service.pendingDepartmentPlans(actor));
        hod.setDeleted(false);hod.setAccountStatus(null);assertThrows(AccessDeniedException.class,()->service.departmentPeriods(actor));
    }
    @Test void incompletePlanCannotSubmit() {
        assertThrows(BadRequestException.class,()->service.submitDepartment(10L,actor));assertEquals(KpiPlanStatus.DRAFT,plan.getStatus());
        complete();plan.getItems().get(0).setWeightage(new java.math.BigDecimal("99.99"));
        assertThrows(BadRequestException.class,()->service.submitDepartment(10L,actor));
        plan.getItems().get(0).setWeightage(new java.math.BigDecimal("100"));plan.getItems().get(0).getScoringDefinitions().remove(5);
        assertThrows(BadRequestException.class,()->service.submitDepartment(10L,actor));verify(assignments,never()).cascade(any());
    }
    @Test void submissionIsWholePlanAndSnapshotsLatenessWithoutCascade() {
        pending();assertEquals(KpiPlanStatus.PENDING_APPROVAL,plan.getStatus());assertEquals(actor,plan.getSubmittedBy());
        assertEquals(OffsetDateTime.now(clock),plan.getSubmittedAt());assertTrue(plan.getSubmittedLate());
        period.setKpiSetupDeadline(LocalDate.of(2028,1,1));assertTrue(plan.getSubmittedLate());
        verify(assignments,never()).cascade(any());verify(periods).lockConfiguration();verify(plans).lockById(10L);
    }
    @Test void pendingContentCannotBeEditedOrSubmittedAgain() {
        pending();assertThrows(BadRequestException.class,()->service.updateDepartment(10L,request,actor));
        assertThrows(BadRequestException.class,()->service.submitDepartment(10L,actor));
    }
    @Test void returnRequiresReasonAndDoesNotCascade() {
        pending();
        for(String value:Arrays.asList(null,"","  ","x".repeat(10001)))
            assertThrows(BadRequestException.class,()->service.returnDepartment(10L,reason(value),actor));
        var returned=service.returnDepartment(10L,reason(" Clarify sales target "),actor);
        assertEquals(KpiPlanStatus.RETURNED,returned.getStatus());assertEquals("Clarify sales target",returned.getReturnReason());
        assertEquals(actor,returned.getReviewedBy());assertTrue(returned.getReviewedLate());verify(assignments,never()).cascade(any());
    }
    @Test void returnedPlanRetainsReasonDuringEditingAndCanResubmit() {
        pending();service.returnDepartment(10L,reason("Clarify target"),actor);
        request.setItems(List.of(KpiPlanValidatorTest.item("Updated sales","100")));
        assertEquals("Clarify target",service.updateDepartment(10L,request,actor).getReturnReason());
        var result=service.submitDepartment(10L,actor);assertEquals(KpiPlanStatus.PENDING_APPROVAL,result.getStatus());
        assertNull(result.getReturnReason());assertNull(result.getReviewedBy());assertNull(result.getReviewedAt());
    }
    @Test void returnedDepartmentRejectsUnchangedSaveButAcceptsChangedTarget() {
        pending();service.returnDepartment(10L,reason("Clarify target"),actor);
        assertTrue(service.departmentPlan(10L,actor).isRevisionRequired());
        assertThrows(BadRequestException.class,()->service.submitDepartment(10L,actor));
        request.setItems(service.departmentPlan(10L,actor).getItems());
        service.updateDepartment(10L,request,actor);
        assertTrue(plan.isRevisionRequired());
        assertThrows(BadRequestException.class,()->service.submitDepartment(10L,actor));
        request.getItems().get(0).setTarget("RM 90,000 monthly sales");
        assertFalse(service.updateDepartment(10L,request,actor).isRevisionRequired());
        assertEquals(KpiPlanStatus.PENDING_APPROVAL,service.submitDepartment(10L,actor).getStatus());
        service.returnDepartment(10L,reason("Another change needed"),actor);
        assertTrue(plan.isRevisionRequired());
    }
    @Test void reorderingOrRecreatingIdenticalItemsDoesNotCountButRemovingAnItemDoes() {
        request.setItems(List.of(KpiPlanValidatorTest.item("Sales","60"),KpiPlanValidatorTest.item("Customers","40")));
        service.updateDepartment(10L,request,actor);service.submitDepartment(10L,actor);
        service.returnDepartment(10L,reason("Clarify"),actor);
        request.setItems(List.of(KpiPlanValidatorTest.item("Customers","40.00"),KpiPlanValidatorTest.item("Sales","60.0")));
        service.updateDepartment(10L,request,actor);
        assertTrue(plan.isRevisionRequired());
        assertThrows(BadRequestException.class,()->service.submitDepartment(10L,actor));
        request.setItems(List.of(KpiPlanValidatorTest.item("Sales","60")));
        assertFalse(service.updateDepartment(10L,request,actor).isRevisionRequired());
        assertThrows(BadRequestException.class,()->service.submitDepartment(10L,actor)); // Total still must be 100%.
    }
    @Test void approvalCascadesOnceAndBecomesImmutable() {
        pending();var dto=service.approveDepartment(10L,actor);
        assertEquals(KpiPlanStatus.APPROVED,dto.getStatus());assertFalse(dto.isOverdue());assertTrue(dto.getReviewedLate());
        verify(assignments).requirePublishedRoster(period);verify(assignments).cascade(plan);
        assertThrows(BadRequestException.class,()->service.approveDepartment(10L,actor));
        assertThrows(BadRequestException.class,()->service.returnDepartment(10L,reason("Changed mind"),actor));
        assertThrows(BadRequestException.class,()->service.updateDepartment(10L,request,actor));verify(assignments,times(1)).cascade(plan);
    }
    @Test void approvalRechecksCompletenessAndPublishedRoster() {
        pending();plan.getItems().clear();assertThrows(BadRequestException.class,()->service.approveDepartment(10L,actor));
        complete();doThrow(new BadRequestException("Roster required")).when(assignments).requirePublishedRoster(period);
        assertThrows(BadRequestException.class,()->service.approveDepartment(10L,actor));
        assertEquals(KpiPlanStatus.PENDING_APPROVAL,plan.getStatus());verify(assignments,never()).cascade(any());
    }
    @Test void draftAndReturnedCannotReceiveDecision() {
        assertThrows(BadRequestException.class,()->service.approveDepartment(10L,actor));
        plan.setStatus(KpiPlanStatus.RETURNED);assertThrows(BadRequestException.class,()->service.returnDepartment(10L,reason("Fix"),actor));
    }
    @Test void closedPeriodsRejectAllWritesButAllowDetails() {
        pending();period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);
        assertThrows(BadRequestException.class,()->service.approveDepartment(10L,actor));
        assertThrows(BadRequestException.class,()->service.returnDepartment(10L,reason("Fix"),actor));
        assertThrows(BadRequestException.class,()->service.createDepartment(request,actor));
        assertThrows(BadRequestException.class,()->service.updateDepartment(10L,request,actor));
        assertThrows(BadRequestException.class,()->service.submitDepartment(10L,actor));
        assertEquals(AnnualKpiReviewPeriodStatus.CLOSED,service.departmentPlan(10L,actor).getReviewPeriodStatus());
    }
    @Test void assignmentFailureRollsBackApprovalTransaction() {
        pending();doThrow(new IllegalStateException("Assignment failure")).when(assignments).cascade(plan);
        var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status=mock(org.springframework.transaction.TransactionStatus.class);when(manager.getTransaction(any())).thenReturn(status);
        var proxy=new org.springframework.aop.framework.ProxyFactory(service);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional=(KpiPlanService)proxy.getProxy();
        assertThrows(IllegalStateException.class,()->transactional.approveDepartment(10L,actor));
        verify(manager).rollback(status);verify(manager,never()).commit(any());
    }
}
