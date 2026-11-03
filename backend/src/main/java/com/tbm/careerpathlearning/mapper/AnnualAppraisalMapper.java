package com.tbm.careerpathlearning.mapper;

import com.tbm.careerpathlearning.dto.AnnualAppraisalDto;
import com.tbm.careerpathlearning.model.*;
import org.mapstruct.*;

@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface AnnualAppraisalMapper {
    @Mapping(target="participantId",source="participant.id")
    @Mapping(target="employeeName",source="participant.staffName")
    @Mapping(target="departmentName",source="participant.departmentName")
    @Mapping(target="roleName",source="participant.roleName")
    @Mapping(target="reviewPeriodName",source="participant.reviewPeriod.name")
    @Mapping(target="reviewPeriodStatus",source="participant.reviewPeriod.status")
    @Mapping(target="recommendationDeadline",source="participant.reviewPeriod.appraisalRecommendationDeadline")
    @Mapping(target="hrReviewDeadline",source="participant.reviewPeriod.hrFinalisationDeadline")
    @Mapping(target="recommendationOverdue",ignore=true) @Mapping(target="hrReviewOverdue",ignore=true)
    @Mapping(target="sourceChanged",ignore=true) @Mapping(target="readiness",ignore=true)
    @Mapping(target="submissionBlockers",ignore=true) @Mapping(target="reviewBlockers",ignore=true)
    @Mapping(target="allowedActions",ignore=true) @Mapping(target="activity",ignore=true)
    AnnualAppraisalDto toDto(AnnualAppraisal appraisal);
    AnnualAppraisalDto.Activity toDto(AnnualAppraisalActivity activity);
}
