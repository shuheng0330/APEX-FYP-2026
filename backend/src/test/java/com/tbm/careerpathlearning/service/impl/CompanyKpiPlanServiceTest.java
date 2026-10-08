package com.tbm.careerpathlearning.service.impl;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.mapper.KpiPlanMapper;
import com.tbm.careerpathlearning.repository.*;
import com.tbm.careerpathlearning.service.KpiPlanValidator;
import com.tbm.careerpathlearning.exception.BadRequestException;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class CompanyKpiPlanServiceTest {
    KpiPlanRepository plans=mock(KpiPlanRepository.class);
    AnnualKpiReviewPeriodRepository periods=mock(AnnualKpiReviewPeriodRepository.class);
    com.tbm.careerpathlearning.service.KpiAssignmentService assignments=mock(com.tbm.careerpathlearning.service.KpiAssignmentService.class);
    KpiPlanServiceImpl service=new KpiPlanServiceImpl(plans,periods,Mappers.getMapper(KpiPlanMapper.class),new KpiPlanValidator(),Clock.systemUTC(),assignments,
        mock(StaffRepository.class),mock(ReviewPeriodParticipantRepository.class),mock(OrgChartRepository.class),
        mock(com.tbm.careerpathlearning.service.PerformanceDepartmentResolver.class));
    AnnualKpiReviewPeriod period; KpiPlanRequest request;
    @BeforeEach void setup() {
        period=new AnnualKpiReviewPeriod();period.setId(1L);period.setStatus(AnnualKpiReviewPeriodStatus.OPEN);
        when(periods.findById(1L)).thenReturn(Optional.of(period));
        when(plans.saveAndFlush(any())).thenAnswer(i->{KpiPlan p=i.getArgument(0);p.setId(10L);return p;});
        request=new KpiPlanRequest();request.setReviewPeriodId(1L);request.setItems(List.of(new KpiItemDto()));
    }
    @Test void openPeriodAllowsIncompleteLateDraft() {period.setKpiSetupDeadline(LocalDate.of(2000,1,1));var dto=service.createCompany(request,UUID.randomUUID());assertTrue(dto.isOverdue());assertEquals(KpiPlanStatus.DRAFT,dto.getStatus());}
    @Test void closedPeriodIsReadOnly() {period.setStatus(AnnualKpiReviewPeriodStatus.CLOSED);assertThrows(BadRequestException.class,()->service.createCompany(request,UUID.randomUUID()));verify(plans,never()).saveAndFlush(any());}
    @Test void duplicatePlanCannotBeCreated() {when(plans.findByReviewPeriodIdAndLevel(1L,KpiLevel.COMPANY)).thenReturn(Optional.of(new KpiPlan()));assertThrows(BadRequestException.class,()->service.createCompany(request,UUID.randomUUID()));}
    @Test void cannotInjectItemFromAnotherPlan() {var input=new KpiItemDto();input.setId(99L);request.setItems(List.of(input));assertThrows(BadRequestException.class,()->service.createCompany(request,UUID.randomUUID()));}
    @Test void companyCannotHaveDepartmentScope() {request.setDepartmentId(2L);assertThrows(BadRequestException.class,()->service.createCompany(request,UUID.randomUUID()));}
    @Test void latePublicationSnapshotsLatenessAndCascadesWholePlanOnce() {
        period.setKpiSetupDeadline(LocalDate.of(2000,1,1));var plan=new KpiPlan();plan.setId(10L);plan.setLevel(KpiLevel.COMPANY);plan.setReviewPeriod(period);
        var input=com.tbm.careerpathlearning.service.KpiPlanValidatorTest.item("Sales","100");var item=new Kpi();Mappers.getMapper(KpiPlanMapper.class).update(input,item);plan.getItems().add(item);
        when(plans.lockById(10L)).thenReturn(Optional.of(plan));
        var dto=service.publishCompany(10L,UUID.randomUUID());assertEquals(KpiPlanStatus.PUBLISHED,dto.getStatus());assertTrue(dto.getPublishedLate());
        period.setKpiSetupDeadline(LocalDate.of(2100,1,1));assertTrue(plan.getPublishedLate());
        assertThrows(BadRequestException.class,()->service.publishCompany(10L,UUID.randomUUID()));verify(assignments,times(1)).cascade(plan);
    }
    @Test void incompletePlanCannotPublishOrCascade() {
        var plan=new KpiPlan();plan.setId(10L);plan.setLevel(KpiLevel.COMPANY);plan.setReviewPeriod(period);
        when(plans.lockById(10L)).thenReturn(Optional.of(plan));
        assertThrows(BadRequestException.class,()->service.publishCompany(10L,UUID.randomUUID()));
        assertEquals(KpiPlanStatus.DRAFT,plan.getStatus());verify(assignments,never()).cascade(any());
    }
    @Test void publicationTransactionRollsBackIfCascadeFails() {
        var plan=new KpiPlan();plan.setId(10L);plan.setLevel(KpiLevel.COMPANY);plan.setReviewPeriod(period);
        var item=new Kpi();Mappers.getMapper(KpiPlanMapper.class).update(
            com.tbm.careerpathlearning.service.KpiPlanValidatorTest.item("Sales","100"),item);plan.getItems().add(item);
        when(plans.lockById(10L)).thenReturn(Optional.of(plan));
        doThrow(new IllegalStateException("Assignment write failed")).when(assignments).cascade(plan);
        var manager=mock(org.springframework.transaction.PlatformTransactionManager.class);
        var status=mock(org.springframework.transaction.TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(status);
        var proxy=new org.springframework.aop.framework.ProxyFactory(service);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(manager,
            new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactional=(com.tbm.careerpathlearning.service.KpiPlanService)proxy.getProxy();
        assertThrows(IllegalStateException.class,()->transactional.publishCompany(10L,UUID.randomUUID()));
        verify(manager).rollback(status);verify(manager,never()).commit(any());
    }
}
