package com.tbm.careerpathlearning.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Only appraisal-authorised users receive this DTO; never included in employee dashboards. */
public final class EmployeeInsightDto {
    private EmployeeInsightDto() {}
    public record Limits(OffsetDateTime serverTime, int dailyAllowance, int remainingAttempts,
            OffsetDateTime resetAt, OffsetDateTime cooldownUntil, OffsetDateTime providerRetryAt) {}
    public record Insight(UUID id, Integer version, String status, boolean outdated, boolean fictional,
            String model, String templateVersion, OffsetDateTime createdAt, OffsetDateTime completedAt,
            String errorCode, OffsetDateTime retryAt, AppraisalInsightDto.Evidence evidence,
            AppraisalInsightDto.Summary summary, String disclaimer) {}
    public record State(Long participantId, boolean eligible, String unavailableReason, Insight currentInsight,
            Insight latestAttempt, List<Insight> history, Limits limits) {}
    public record Result(String disposition, Insight generation, Limits limits) {}
    public record LimitError(String reason, String message, OffsetDateTime retryAt, Limits limits) {}
    public record Submitted(Long appraisalId, Long submissionActivityId, Insight insight) {}
}
