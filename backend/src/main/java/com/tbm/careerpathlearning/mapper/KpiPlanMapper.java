package com.tbm.careerpathlearning.mapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.model.*;
import org.mapstruct.*;
@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface KpiPlanMapper {
    @Mapping(target="reviewPeriodId",source="reviewPeriod.id")
    @Mapping(target="reviewPeriodName",source="reviewPeriod.name")
    @Mapping(target="reviewPeriodStatus",source="reviewPeriod.status")
    @Mapping(target="kpiSetupDeadline",source="reviewPeriod.kpiSetupDeadline")
    @Mapping(target="departmentId",source="department.id")
    @Mapping(target="departmentName",source="department.name")
    @Mapping(target="employeeName",source="ownerParticipant.staffName")
    @Mapping(target="submittedByName",source="submitter.name")
    @Mapping(target="submittedToSuperiorName",source="submittedToSuperior.name")
    @Mapping(target="totalWeightage",ignore=true) @Mapping(target="overdue",ignore=true)
    KpiPlanDto toDto(KpiPlan plan);
    KpiItemDto toDto(Kpi item);
    @BeanMapping(ignoreByDefault=true)
    @Mapping(target="name",source="name") @Mapping(target="description",source="description")
    @Mapping(target="perspective",source="perspective") @Mapping(target="kra",source="kra")
    @Mapping(target="target",source="target") @Mapping(target="measurementUnit",source="measurementUnit")
    @Mapping(target="weightage",source="weightage") @Mapping(target="scoringDefinitions",source="scoringDefinitions")
    void update(KpiItemDto request,@MappingTarget Kpi item);
    @Mapping(target="kpiAllocation",ignore=true)
    KpiPeriodContextDto toContext(AnnualKpiReviewPeriod period);
    @Mapping(target="employeeLevelId",source="employeeLevel.id")
    @Mapping(target="employeeLevelName",source="employeeLevel.name")
    @Mapping(target="employeeLevelCode",source="employeeLevel.code")
    ReviewPeriodEmployeeLevelConfigurationDto toDto(ReviewPeriodEmployeeLevelConfiguration configuration);
}
