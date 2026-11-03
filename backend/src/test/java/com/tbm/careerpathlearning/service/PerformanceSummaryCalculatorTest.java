package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.StaffPerformanceDto.Scores;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.Bin;
import com.tbm.careerpathlearning.dto.TeamPerformanceDto.Metric;
import com.tbm.careerpathlearning.model.ReviewPeriodParticipant;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceSummaryCalculatorTest {
    @ParameterizedTest
    @EnumSource(Metric.class)
    void groupsUnroundedDecimalScoresAtEveryBoundaryWithoutChangingScores(Metric metric) {
        var population = new ArrayList<ReviewPeriodParticipant>();
        var scores = new HashMap<Long, Scores>();
        for (String raw : Arrays.asList("0", "19.5", "59.99999999", "60", "60.00000001", "69", "69.99999999",
                "70", "79", "79.99999999", "80", "89", "89.99999999", "90", "99.99999999", "100", null)) {
            var participant = new ReviewPeriodParticipant(); participant.setId((long) population.size() + 1); population.add(participant);
            var score = raw == null ? null : new BigDecimal(raw);
            scores.put(participant.getId(), new Scores(score, score, score, null, null, null));
        }
        var originalScores = Map.copyOf(scores);
        var data = new TeamPerformanceResults.Data(scores, Map.of(), Map.of(), Map.of(), List.of(), List.of());
        var distribution = PerformanceSummaryCalculator.distribution(population, data, metric);
        assertEquals(List.of(new Bin(0, 60, false, 3), new Bin(60, 70, false, 4), new Bin(70, 80, false, 3),
                new Bin(80, 90, false, 3), new Bin(90, 100, true, 3)), distribution.bins());
        assertEquals(metric, distribution.metric()); assertEquals(16, distribution.employeesWithResults());
        assertEquals(1, distribution.missingResults()); assertEquals(originalScores, scores);
        int[] expectedBands = {0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4};
        for (int i = 0; i < expectedBands.length; i++) {
            var single = PerformanceSummaryCalculator.distribution(List.of(population.get(i)), data, metric);
            assertEquals(1, single.bins().get(expectedBands[i]).employees(), "Score " + scores.get(population.get(i).getId()));
        }
    }

    @Test void metricsKeepIndependentBandsAndMissingResultCounts() {
        var participant = new ReviewPeriodParticipant(); participant.setId(1L);
        var scores = new Scores(new BigDecimal("19.5"), new BigDecimal("69.9999"), null, null, null, null);
        var data = new TeamPerformanceResults.Data(Map.of(1L, scores), Map.of(), Map.of(), Map.of(), List.of(), List.of());
        assertEquals(1, PerformanceSummaryCalculator.distribution(List.of(participant), data, Metric.KPI).bins().get(0).employees());
        assertEquals(1, PerformanceSummaryCalculator.distribution(List.of(participant), data, Metric.ATTITUDE).bins().get(1).employees());
        var pending = PerformanceSummaryCalculator.distribution(List.of(participant), data, Metric.FINAL);
        assertEquals(0, pending.employeesWithResults()); assertEquals(1, pending.missingResults());
        assertTrue(pending.bins().stream().allMatch(bin -> bin.employees() == 0));
    }

    @Test void emptyPopulationRetainsTheFiveBandsWithoutInventingResults() {
        var data = new TeamPerformanceResults.Data(Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of());
        var distribution = PerformanceSummaryCalculator.distribution(List.of(), data, Metric.FINAL);
        assertEquals(List.of(new Bin(0, 60, false, 0), new Bin(60, 70, false, 0), new Bin(70, 80, false, 0),
                new Bin(80, 90, false, 0), new Bin(90, 100, true, 0)), distribution.bins());
        assertEquals(0, distribution.employeesWithResults()); assertEquals(0, distribution.missingResults());
    }
}
