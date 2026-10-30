package com.tbm.careerpathlearning.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tbm.careerpathlearning.enums.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Read-only dashboard contracts. Internal appraisal fields are never populated for self views. */
public record StaffPerformanceDto(Employee employee, Period period, Scores scores,
        Long defaultCheckpointId, List<Checkpoint> checkpoints, Attitude attitude,
        ApprovedAppraisal approvedAppraisal, List<ApprovedAppraisal> appraisalHistory,
        List<TrendPoint> trend, Capabilities capabilities) {
    public record Employee(UUID id, String name, String departmentName, String roleName) {}
    public record Period(Long participantId, Long id, String name, LocalDate startDate, LocalDate endDate,
            AnnualKpiReviewPeriodStatus status, ReviewFrequency reviewFrequency,
            KpiFrequencyPolicy frequencyPolicy, KpiAssessmentModel assessmentModel) {}
    public record Scores(BigDecimal annualKpiScore, BigDecimal attitudeScore, BigDecimal finalScore,
            BigDecimal kpiWeight, BigDecimal attitudeWeight, AnnualKpiConsolidationMethod consolidationMethod) {}
    public record Capabilities(boolean selfView, boolean canViewInternalAppraisal) {}
    public record Checkpoint(Long id, int sequenceNumber, LocalDate startDate, LocalDate endDate,
            Long assessmentId, KpiAssessmentStatus status, BigDecimal score, OffsetDateTime submittedAt,
            OffsetDateTime reviewedAt) {}
    public record CheckpointDetails(Period period, Checkpoint checkpoint, KpiAssessmentModel assessmentModel,
            KpiScoreBreakdownDto breakdown, List<KpiAnswer> answers) {}
    public record KpiAnswer(Long id, KpiLevel level, KpiItemDto kpi, Integer selfPoint, String selfComment,
            Integer superiorPoint, String superiorComment) {}
    public record Attitude(Long assessmentId, Long configurationId, String configurationName,
            AttitudeEvaluationFormat format, AttitudeAssessmentStatus status, BigDecimal score,
            OffsetDateTime submittedAt, OffsetDateTime reviewedAt,
            List<AttitudeCriterionResult> criteria, List<Rating> ratingDefinitions) {}
    public record AttitudeCriterionResult(Long id, String name, String description,
            AttitudeCriterionType criterionType, Integer selfPoint, String selfComment,
            Integer superiorPoint, String superiorComment) {}
    public record Rating(Integer point, String label, String description) {}
    public enum SourceType { LATEST_COMPLETED_RESULTS }
    public enum RecordedComparison { UNCHANGED, DIFFERENT, CURRENT_RESULTS_UNAVAILABLE, NOT_APPLICABLE }
    public record TrendPoint(Period period, Scores scores, SourceType sourceType) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApprovedAppraisal(Long id, Period period, AppraisalStatus status,
            AnnualAppraisalRecommendation finalDecision, OffsetDateTime approvedAt,
            Scores recordedScores, RecordedComparison comparisonStatus, InternalAppraisal internalDetails) {}
    public record InternalAppraisal(AnnualAppraisalRecommendation recommendation, String justification,
            String overrideReason, Boolean reviewedLate, AnnualAppraisalSnapshotDto recordedPerformance) {}
}
