package com.tbm.careerpathlearning.mapper;

import com.tbm.careerpathlearning.dto.AttitudeAssessmentDto;
import com.tbm.careerpathlearning.model.*;
import org.mapstruct.*;

@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface AttitudeAssessmentMapper {
    @BeanMapping(ignoreByDefault=true)
    @Mapping(target="id",source="id") @Mapping(target="status",source="status")
    @Mapping(target="evaluationFormat",source="evaluationFormat")
    @Mapping(target="configurationId",source="configuration.id")
    @Mapping(target="configurationName",source="configuration.name")
    @Mapping(target="createdAt",source="createdAt") @Mapping(target="updatedAt",source="updatedAt")
    @Mapping(target="submittedAt",source="submittedAt") @Mapping(target="submittedBy",source="submittedBy")
    @Mapping(target="submittedToSuperiorId",source="submittedToSuperiorId") @Mapping(target="submittedLate",source="submittedLate")
    AttitudeAssessmentDto toDto(AttitudeAssessment assessment);

    @Mapping(target="criterionId",source="criterion.id")
    @Mapping(target="name",source="criterion.name") @Mapping(target="description",source="criterion.description")
    @Mapping(target="criterionType",source="criterion.criterionType") @Mapping(target="displayOrder",source="criterion.displayOrder")
    AttitudeAssessmentDto.Item toDto(AttitudeAssessmentItem item);
}
