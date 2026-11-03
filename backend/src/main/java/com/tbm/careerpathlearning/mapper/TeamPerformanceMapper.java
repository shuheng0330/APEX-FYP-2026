package com.tbm.careerpathlearning.mapper;

import com.tbm.careerpathlearning.dto.TeamPerformanceDto.Period;
import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import org.mapstruct.*;

@Mapper(componentModel="spring")
public interface TeamPerformanceMapper {
    @Mapping(target="consolidationMethod", source="annualKpiConsolidationMethod")
    @Mapping(target="kpiWeight", source="kpiPerformanceWeight")
    @Mapping(target="attitudeWeight", source="attitudeEvaluationWeight")
    Period toPeriod(AnnualKpiReviewPeriod period);
}
