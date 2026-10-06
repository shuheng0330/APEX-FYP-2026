package com.tbm.careerpathlearning.mapper;

import com.tbm.careerpathlearning.dto.AnnualKpiReviewPeriodDto;
import com.tbm.careerpathlearning.dto.AnnualKpiReviewPeriodRequest;
import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import com.tbm.careerpathlearning.model.ReviewCheckpoint;
import com.tbm.careerpathlearning.model.ReviewPeriodRoleConfiguration;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AnnualKpiReviewPeriodMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "name")
    @Mapping(target = "startDate", source = "startDate")
    @Mapping(target = "endDate", source = "endDate")
    @Mapping(target = "companyKpiWeight", source = "companyKpiWeight")
    @Mapping(target = "departmentKpiWeight", source = "departmentKpiWeight")
    @Mapping(target = "individualKpiWeight", source = "individualKpiWeight")
    @Mapping(target = "kpiPerformanceWeight", source = "kpiPerformanceWeight")
    @Mapping(target = "attitudeEvaluationWeight", source = "attitudeEvaluationWeight")
    @Mapping(target = "annualKpiConsolidationMethod", source = "annualKpiConsolidationMethod")
    @Mapping(target = "companyKpiCreationDeadline", source = "companyKpiCreationDeadline")
    @Mapping(target = "departmentKpiCreationDeadline", source = "departmentKpiCreationDeadline")
    @Mapping(target = "individualKpiSubmissionDeadline", source = "individualKpiSubmissionDeadline")
    @Mapping(target = "individualKpiApprovalDeadline", source = "individualKpiApprovalDeadline")
    @Mapping(target = "selfAssessmentDaysAfterCheckpoint", source = "selfAssessmentDaysAfterCheckpoint")
    @Mapping(target = "superiorAssessmentDaysAfterSelfDeadline", source = "superiorAssessmentDaysAfterSelfDeadline")
    @Mapping(target = "attitudeSelfAssessmentDeadline", source = "attitudeSelfAssessmentDeadline")
    @Mapping(target = "superiorAttitudeEvaluationDeadline", source = "superiorAttitudeEvaluationDeadline")
    @Mapping(target = "appraisalRecommendationDeadline", source = "appraisalRecommendationDeadline")
    @Mapping(target = "hrFinalisationDeadline", source = "hrFinalisationDeadline")
    void updateConfiguration(AnnualKpiReviewPeriodRequest request, @MappingTarget AnnualKpiReviewPeriod period);

    @Mapping(target = "roleConfigurations", ignore = true)
    @Mapping(target = "checkpoints", ignore = true)
    AnnualKpiReviewPeriodDto toDto(AnnualKpiReviewPeriod period);

    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "roleName", source = "role.name")
    @Mapping(target = "departmentName", source = "role.orgChart.name")
    AnnualKpiReviewPeriodDto.RoleConfiguration toDto(ReviewPeriodRoleConfiguration configuration);

    AnnualKpiReviewPeriodDto.Checkpoint toDto(ReviewCheckpoint checkpoint);
}
