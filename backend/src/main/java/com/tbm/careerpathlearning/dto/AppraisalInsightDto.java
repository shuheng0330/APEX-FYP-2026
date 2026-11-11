package com.tbm.careerpathlearning.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Synthetic preview contract; contains no real employee/appraisal identifiers. */
public record AppraisalInsightDto(UUID id, String scenario, String status, boolean synthetic,
        String templateVersion, String model, OffsetDateTime createdAt, OffsetDateTime completedAt,
        String errorCode, Evidence evidence, Summary summary, String disclaimer) {

    public record Scenario(String id, String title) {}
    public record Scores(BigDecimal kpi, BigDecimal attitude, BigDecimal finalAppraisal) {}
    public record Fact(String id, String scope, String text) {}
    public record History(String id, String periodName, LocalDate startDate, LocalDate endDate,
            Scores scores, boolean comparable, String comparisonNote) {}
    public record Evidence(String periodName, LocalDate startDate, LocalDate endDate,
            boolean requiredResultsComplete, String frequency, String consolidationMethod,
            BigDecimal kpiWeight, BigDecimal attitudeWeight, Scores scores,
            List<Fact> facts, List<History> history, List<String> informationGaps) {}
    public record Statement(String text, List<String> evidenceIds) {}
    public record Considerations(Statement promotionDiscussion, Statement salaryIncrementDiscussion) {}
    public record Summary(List<Statement> performanceSummary, List<Statement> keyStrengths,
            List<Statement> areasForImprovement, List<Statement> performanceTrend,
            Considerations appraisalConsiderations, List<Statement> informationGaps) {}
}
