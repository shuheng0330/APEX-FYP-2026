package com.tbm.careerpathlearning.service;
import com.tbm.careerpathlearning.dto.*;
import java.util.*;
public interface KpiPlanService {
    List<KpiPeriodContextDto> companyPeriods();
    List<KpiPlanDto> companyPlans();
    KpiPlanDto companyPlan(Long id);
    KpiPlanDto createCompany(KpiPlanRequest request,UUID actor);
    KpiPlanDto updateCompany(Long id,KpiPlanRequest request,UUID actor);
    KpiPlanDto publishCompany(Long id,UUID actor);
    List<KpiPeriodContextDto> departmentPeriods(UUID actor);
    List<KpiDepartmentOptionDto> departmentOptions(UUID actor);
    List<KpiPlanDto> departmentPlans(UUID actor);
    List<KpiPlanDto> pendingDepartmentPlans(UUID actor);
    KpiPlanDto departmentPlan(Long id,UUID actor);
    KpiPlanDto createDepartment(KpiPlanRequest request,UUID actor);
    KpiPlanDto updateDepartment(Long id,KpiPlanRequest request,UUID actor);
    KpiPlanDto submitDepartment(Long id,UUID actor);
    KpiPlanDto approveDepartment(Long id,UUID actor);
    KpiPlanDto returnDepartment(Long id,KpiPlanReturnRequest request,UUID actor);
}
