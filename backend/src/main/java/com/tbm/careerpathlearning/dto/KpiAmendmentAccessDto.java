package com.tbm.careerpathlearning.dto;
import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import java.time.*;
import java.util.UUID;
public record KpiAmendmentAccessDto(Long reviewPeriodId,String reviewPeriodName,AnnualKpiReviewPeriodStatus periodStatus,
    boolean enabled,LocalDate closeDate,boolean available,boolean expired,OffsetDateTime changedAt,UUID changedBy) {}
