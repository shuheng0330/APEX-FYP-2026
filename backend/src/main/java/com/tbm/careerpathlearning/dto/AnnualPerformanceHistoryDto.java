package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AnnualKpiConsolidationMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Performance only: no recommendation, justification, HR decision or private notes. */
public record AnnualPerformanceHistoryDto(UUID employeeId, Long participantId, Long reviewPeriodId,
        String periodName, LocalDate startDate, LocalDate endDate, BigDecimal annualKpiScore,
        BigDecimal attitudeScore, BigDecimal finalScore, BigDecimal kpiWeight, BigDecimal attitudeWeight,
        AnnualKpiConsolidationMethod consolidationMethod, SourceType sourceType,
        ComparisonStatus comparisonStatus) {
    public enum SourceType { SUBMISSION_SNAPSHOT, CALCULATED_RESULTS }
    public enum ComparisonStatus { UNCHANGED, DIFFERENT, CURRENT_RESULTS_UNAVAILABLE, NOT_APPLICABLE }
}
