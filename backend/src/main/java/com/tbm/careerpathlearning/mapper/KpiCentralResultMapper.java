package com.tbm.careerpathlearning.mapper;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.model.*;
import org.mapstruct.*;

@Mapper(componentModel="spring",uses=KpiPlanMapper.class,unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface KpiCentralResultMapper {
    @Mapping(target="checkpointId",source="checkpoint.id")
    @Mapping(target="planVersionId",source="planVersion.id")
    @Mapping(target="planVersionNumber",source="planVersion.versionNumber")
    @Mapping(target="reviewPeriodName",source="planVersion.plan.reviewPeriod.name")
    @Mapping(target="reviewPeriodStatus",source="planVersion.plan.reviewPeriod.status")
    @Mapping(target="departmentId",source="planVersion.plan.department.id")
    @Mapping(target="departmentName",source="planVersion.plan.department.name")
    @Mapping(target="publicationBlockers",ignore=true)
    @Mapping(target="allowedActions",ignore=true)
    @Mapping(target="workingCorrectionId",ignore=true)
    KpiCentralResultDto toDto(KpiCentralResult result);
    @Mapping(target="evidence",ignore=true)
    KpiCentralResultDto.Item toDto(KpiCentralResultItem item);
    KpiCentralResultEvidenceDto toDto(KpiCentralResultEvidence evidence);
    CompanyKpiResultDelegateDto toDto(CompanyKpiResultDelegate delegate);
}
