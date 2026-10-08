package com.tbm.careerpathlearning.mapper;

import com.tbm.careerpathlearning.dto.*;
import com.tbm.careerpathlearning.model.*;
import org.mapstruct.*;

@Mapper(componentModel="spring",unmappedTargetPolicy=ReportingPolicy.ERROR)
public interface KpiAssistanceMapper {
    @Mapping(target="ownerParticipantId",source="ownerParticipant.id")
    @Mapping(target="employeeId",source="ownerParticipant.staff.id")
    @Mapping(target="employeeName",source="ownerParticipant.staffName")
    @Mapping(target="departmentName",source="ownerParticipant.departmentName")
    @Mapping(target="reviewPeriodId",source="ownerParticipant.reviewPeriod.id")
    @Mapping(target="reviewPeriodName",source="ownerParticipant.reviewPeriod.name")
    @Mapping(target="reviewPeriodStatus",source="ownerParticipant.reviewPeriod.status")
    @Mapping(target="superiorId",source="superior.id") @Mapping(target="superiorName",source="superior.name")
    @Mapping(target="authorizedById",source="authorizedBy.id") @Mapping(target="authorizedByName",source="authorizedBy.name")
    @Mapping(target="planId",ignore=true)
    KpiAssistanceDto toDto(IndividualKpiAssistanceAuthorization authorization);

    @Mapping(target="ownerParticipantId",source="id")
    @Mapping(target="employeeId",source="staff.id") @Mapping(target="employeeName",source="staffName")
    @Mapping(target="reviewPeriodId",source="reviewPeriod.id") @Mapping(target="reviewPeriodName",source="reviewPeriod.name")
    KpiAssistanceEmployeeDto toDto(ReviewPeriodParticipant participant);
}
