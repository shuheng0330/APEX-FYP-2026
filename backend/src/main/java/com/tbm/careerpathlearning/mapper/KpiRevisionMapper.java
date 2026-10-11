package com.tbm.careerpathlearning.mapper;
import com.tbm.careerpathlearning.dto.KpiRevisionDto;
import com.tbm.careerpathlearning.model.KpiPlanVersion;
import org.mapstruct.*;
@Mapper(componentModel="spring",uses=KpiPlanMapper.class,unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface KpiRevisionMapper {
    @Mapping(target="planId",source="plan.id")
    @Mapping(target="reviewPeriodName",source="plan.reviewPeriod.name")
    @Mapping(target="reviewPeriodStatus",source="plan.reviewPeriod.status")
    @Mapping(target="departmentId",source="plan.department.id")
    @Mapping(target="departmentName",source="plan.department.name")
    @Mapping(target="baseVersionId",source="baseVersion.id")
    @Mapping(target="ownerParticipantId",source="plan.ownerParticipantId")
    @Mapping(target="employeeId",source="plan.ownerParticipant.staff.id")
    @Mapping(target="employeeName",source="plan.ownerParticipant.staffName")
    @Mapping(target="totalWeightage",ignore=true) @Mapping(target="changes",ignore=true)
    @Mapping(target="targetChangedItemIds",ignore=true) @Mapping(target="blockers",ignore=true)
    @Mapping(target="allowedActions",ignore=true) @Mapping(target="actorNames",ignore=true)
    KpiRevisionDto toDto(KpiPlanVersion version);
}
