package com.tbm.careerpathlearning.mapper;

import com.tbm.careerpathlearning.dto.StaffPerformanceDto;
import com.tbm.careerpathlearning.model.ReviewPeriodParticipant;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StaffPerformanceMapper {
    @Mapping(target = "participantId", source = "id")
    @Mapping(target = "id", source = "reviewPeriod.id")
    @Mapping(target = "name", source = "reviewPeriod.name")
    @Mapping(target = "startDate", source = "reviewPeriod.startDate")
    @Mapping(target = "endDate", source = "reviewPeriod.endDate")
    @Mapping(target = "status", source = "reviewPeriod.status")
    @Mapping(target = "frequencyPolicy", source = "reviewPeriod.frequencyPolicy")
    @Mapping(target = "assessmentModel", source = "reviewPeriod.assessmentModel")
    StaffPerformanceDto.Period period(ReviewPeriodParticipant participant);

    @Mapping(target = "id", source = "staff.id")
    @Mapping(target = "name", source = "staffName")
    StaffPerformanceDto.Employee employee(ReviewPeriodParticipant participant);
}
