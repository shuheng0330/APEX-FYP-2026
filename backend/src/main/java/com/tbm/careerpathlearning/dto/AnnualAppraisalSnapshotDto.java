package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/** Stored at submission; source references and definitions are never fetched into old snapshots again. */
public record AnnualAppraisalSnapshotDto(Long participantId, Long reviewPeriodId, UUID employeeId,
        ReviewFrequency reviewFrequency, AnnualKpiConsolidationMethod consolidationMethod,
        BigDecimal kpiWeight, BigDecimal attitudeWeight, BigDecimal annualKpiScore,
        BigDecimal attitudeScore, BigDecimal finalScore, List<Checkpoint> checkpoints, Attitude attitude) {
    public record Checkpoint(Long checkpointId, int sequenceNumber, LocalDate startDate, LocalDate endDate,
            Long assessmentId, KpiAssessmentStatus status, BigDecimal score, OffsetDateTime reviewedAt,
            List<Version> versions, KpiScoreBreakdownDto breakdown) {}
    public record Version(KpiLevel level, Long versionId, int versionNumber) {}
    public record Attitude(Long assessmentId, Long configurationId, AttitudeEvaluationFormat format,
            AttitudeAssessmentStatus status, OffsetDateTime reviewedAt, List<Criterion> criteria) {}
    public record Criterion(Long criterionId, String name, String description, Integer superiorPoint) {}
}
