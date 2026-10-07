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
    KpiPlanServiceImpl service=new KpiPlanServiceImpl(plans,periods,Mappers.getMapper(KpiPlanMapper.class),new KpiPlanValidator(),Clock.systemUTC());
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
}
