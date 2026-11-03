package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.KpiLevel;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record KpiScoreBreakdownDto(Long assessmentId, BigDecimal checkpointScore, List<Level> levels) {
    public enum Availability { PENDING, AVAILABLE, NOT_APPLICABLE }
    public record Level(KpiLevel level, BigDecimal allocation, Integer versionNumber,
            Integer resultEditionNumber, OffsetDateTime publishedAt, Availability status,
            BigDecimal contribution, List<Item> items, ReportingCheckpoint reportingCheckpoint) {
        public Level(KpiLevel level, BigDecimal allocation, Integer versionNumber, Integer resultEditionNumber,
                OffsetDateTime publishedAt, Availability status, BigDecimal contribution, List<Item> items) {
            this(level, allocation, versionNumber, resultEditionNumber, publishedAt, status, contribution, items, null);
        }
    }
    public record ReportingCheckpoint(Long id, com.tbm.careerpathlearning.enums.ReviewFrequency reviewFrequency,
            java.time.LocalDate startDate, java.time.LocalDate endDate) {}
    public record Item(KpiItemDto kpi, String actualAchievement, Integer point) {}
}
