package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.dto.*;
import java.util.*;
public interface KpiPlanService {
    List<KpiPeriodContextDto> companyPeriods();
    List<KpiPlanDto> companyPlans();
    KpiPlanDto companyPlan(Long id);
    KpiPlanDto createCompany(KpiPlanRequest request,UUID actor);
    KpiPlanDto updateCompany(Long id,KpiPlanRequest request,UUID actor);
}
