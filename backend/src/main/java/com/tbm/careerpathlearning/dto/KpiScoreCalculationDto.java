package com.tbm.careerpathlearning.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record KpiScoreCalculationDto(BigDecimal previousScore,BigDecimal score,OffsetDateTime calculatedAt,
        Long companyResultId,Long departmentResultId,UUID triggeredBy,String reason) {}
