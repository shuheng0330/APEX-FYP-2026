package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.AnnualKpiConsolidationMethod;
import java.math.*;
import java.util.List;

/** Shared annual arithmetic. A final checkpoint does not waive earlier completion. */
public final class AnnualPerformanceCalculation {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private AnnualPerformanceCalculation() {}

    public static boolean validScore(BigDecimal score) {
        return score != null && score.signum() >= 0 && score.compareTo(HUNDRED) <= 0;
    }

    public static boolean validWeights(BigDecimal kpi, BigDecimal attitude) {
        return validScore(kpi) && validScore(attitude) && kpi.add(attitude).compareTo(HUNDRED) == 0;
    }

    public static BigDecimal annualKpi(List<BigDecimal> orderedScores, AnnualKpiConsolidationMethod method) {
        if (method == null || orderedScores.isEmpty() || orderedScores.stream().anyMatch(s -> !validScore(s))) return null;
        return method == AnnualKpiConsolidationMethod.FINAL_CHECKPOINT
                ? orderedScores.get(orderedScores.size() - 1).setScale(4, RoundingMode.HALF_UP)
                : orderedScores.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(orderedScores.size()), 4, RoundingMode.HALF_UP);
    }

    public static BigDecimal finalScore(BigDecimal kpi, BigDecimal attitude, BigDecimal kpiWeight, BigDecimal attitudeWeight) {
        if (!validScore(kpi) || !validScore(attitude) || !validWeights(kpiWeight, attitudeWeight)) return null;
        return kpi.multiply(kpiWeight).add(attitude.multiply(attitudeWeight)).divide(HUNDRED, 4, RoundingMode.HALF_UP);
    }
}
