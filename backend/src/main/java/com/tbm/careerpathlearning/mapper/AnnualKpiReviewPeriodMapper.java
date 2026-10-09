package com.tbm.careerpathlearning.mapper;

import com.tbm.careerpathlearning.dto.AnnualKpiReviewPeriodDto;
import com.tbm.careerpathlearning.dto.AnnualKpiReviewPeriodRequest;
import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import com.tbm.careerpathlearning.model.ReviewCheckpoint;
import com.tbm.careerpathlearning.model.ReviewPeriodRoleConfiguration;
import com.tbm.careerpathlearning.model.EmployeeLevel;
import com.tbm.careerpathlearning.model.ReviewPeriodEmployeeLevelConfiguration;
import com.tbm.careerpathlearning.dto.EmployeeLevelDto;
import com.tbm.careerpathlearning.dto.ReviewPeriodEmployeeLevelConfigurationDto;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AnnualKpiReviewPeriodMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "startDate", source = "startDate")
    @Mapping(target = "endDate", source = "endDate")
    @Mapping(target = "kpiPerformanceWeight", source = "kpiPerformanceWeight")
    @Mapping(target = "attitudeEvaluationWeight", source = "attitudeEvaluationWeight")
    @Mapping(target = "annualKpiConsolidationMethod", source = "annualKpiConsolidationMethod")
    @Mapping(target = "kpiSetupDeadline", source = "kpiSetupDeadline")
    @Mapping(target = "selfAssessmentDaysAfterCheckpoint", source = "selfAssessmentDaysAfterCheckpoint")
    @Mapping(target = "superiorAssessmentDaysAfterSelfDeadline", source = "superiorAssessmentDaysAfterSelfDeadline")
    @Mapping(target = "attitudeSelfAssessmentDeadline", source = "attitudeSelfAssessmentDeadline")
    @Mapping(target = "superiorAttitudeEvaluationDeadline", source = "superiorAttitudeEvaluationDeadline")
    @Mapping(target = "appraisalRecommendationDeadline", source = "appraisalRecommendationDeadline")
    @Mapping(target = "hrFinalisationDeadline", source = "hrFinalisationDeadline")
    void updateConfiguration(AnnualKpiReviewPeriodRequest request, @MappingTarget AnnualKpiReviewPeriod period);

    @Mapping(target = "roleConfigurations", ignore = true)
    @Mapping(target = "checkpoints", ignore = true)
    @Mapping(target = "employeeLevelConfigurations", ignore = true)
    @Mapping(target = "editMode", ignore = true)
    @Mapping(target = "canDelete", ignore = true)
    @Mapping(target = "attitudeConfigurationId", source = "attitudeConfiguration.id")
    @Mapping(target = "attitudeConfigurationName", source = "attitudeConfiguration.name")
    AnnualKpiReviewPeriodDto toDto(AnnualKpiReviewPeriod period);

    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "roleName", source = "role.name")
    @Mapping(target = "departmentName", source = "role.orgChart.name")
    @Mapping(target = "employeeLevelId", source = "employeeLevelConfiguration.employeeLevel.id")
    @Mapping(target = "employeeLevelName", source = "employeeLevelConfiguration.employeeLevel.name")
    AnnualKpiReviewPeriodDto.RoleConfiguration toDto(ReviewPeriodRoleConfiguration configuration);

    AnnualKpiReviewPeriodDto.Checkpoint toDto(ReviewCheckpoint checkpoint);

    EmployeeLevelDto toDto(EmployeeLevel level);

    @Mapping(target = "employeeLevelId", source = "employeeLevel.id")
    @Mapping(target = "employeeLevelName", source = "employeeLevel.name")
    @Mapping(target = "employeeLevelCode", source = "employeeLevel.code")
    ReviewPeriodEmployeeLevelConfigurationDto toDto(ReviewPeriodEmployeeLevelConfiguration configuration);
}
