package com.tbm.careerpathlearning.dto;

import java.util.List;

public record TeamReviewPeriodsDto(List<KpiPeriodContextDto> periods, Long defaultPeriodId) {}
