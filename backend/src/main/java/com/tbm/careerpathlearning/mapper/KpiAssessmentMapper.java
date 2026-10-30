package com.tbm.careerpathlearning.mapper;
import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.model.*;
import org.mapstruct.*;
@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface KpiAssessmentMapper {
    KpiAssessmentEvidenceDto toDto(KpiAssessmentEvidence evidence);

    @BeanMapping(ignoreByDefault=true)
    @Mapping(target="id",source="id") @Mapping(target="status",source="status")
    @Mapping(target="createdAt",source="createdAt") @Mapping(target="updatedAt",source="updatedAt")
    @Mapping(target="submittedAt",source="submittedAt") @Mapping(target="submittedBy",source="submittedBy")
    @Mapping(target="submittedToSuperiorId",source="submittedToSuperiorId") @Mapping(target="submittedLate",source="submittedLate")
    @Mapping(target="reviewedAt",source="reviewedAt") @Mapping(target="reviewedBy",source="reviewedBy")
    @Mapping(target="reviewedLate",source="reviewedLate") @Mapping(target="checkpointScore",source="checkpointScore")
    KpiAssessmentDto toDto(KpiAssessment assessment);
}
