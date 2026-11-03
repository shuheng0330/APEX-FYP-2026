package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.*;
import lombok.Data;
import java.time.*;
import java.util.*;

@Data
public class AnnualAppraisalDto {
    private Long id, participantId, reviewPeriodId;
    private UUID employeeId, superiorId;
    private String employeeName, departmentName, roleName, reviewPeriodName;
    private AnnualKpiReviewPeriodStatus reviewPeriodStatus;
    private AppraisalStatus status;
    private AnnualAppraisalRecommendation recommendation, finalDecision;
    private String justification, overrideReason, latestReturnReason;
    private AnnualAppraisalSnapshotDto snapshot;
    private OffsetDateTime createdAt, updatedAt, submittedAt, reviewedAt;
    private UUID createdBy, updatedBy, reviewedBy;
    private Boolean submittedLate, reviewedLate;
    private long lockVersion;
    private LocalDate recommendationDeadline, hrReviewDeadline;
    private boolean recommendationOverdue, hrReviewOverdue, sourceChanged;
    private AnnualAppraisalReadinessDto readiness;
    private List<String> submissionBlockers=List.of(), reviewBlockers=List.of(), allowedActions=List.of();
    private List<Activity> activity=List.of();
    public record Activity(Long id, String action, UUID actorId, OffsetDateTime occurredAt, String reason,
            AnnualAppraisalRecommendation recommendation, String justification,
            AnnualAppraisalRecommendation finalDecision, AnnualAppraisalSnapshotDto snapshot) {}
}
