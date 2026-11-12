package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import java.time.*;
import java.util.List;

/** Completion counts are tasks; employee counts are distinct publication-time participants. */
public record ReviewPeriodClosureDto(Long reviewPeriodId, AnnualKpiReviewPeriodStatus status,
        boolean canClose, LocalDate earliestCloseDate, OffsetDateTime checkedAt,
        OffsetDateTime closedAt, String closedByName, int totalEmployees, int completedEmployees,
        Completion kpiReviews, Completion attitudeEvaluations, List<Blocker> blockers) {
    public record Completion(int required, int completed) {}
    public record Blocker(Code code, Long participantId, String employeeName, String departmentName,
            Long checkpointId, LocalDate checkpointStartDate, LocalDate checkpointEndDate, String message) {}
    public enum Code {
        PERIOD_NOT_OPEN, MEASUREMENT_NOT_ENDED, MISSING_ROSTER, EMPTY_ROSTER, CONFIGURATION,
        KPI_SELF_ASSESSMENT, KPI_SUPERIOR_REVIEW, COMPANY_RESULT, DEPARTMENT_RESULT, KPI_SCORE,
        ATTITUDE_SELF_ASSESSMENT, ATTITUDE_SUPERIOR_REVIEW, ATTITUDE_SCORE, FINAL_SCORE, HR_RECONCILIATION
    }
}
