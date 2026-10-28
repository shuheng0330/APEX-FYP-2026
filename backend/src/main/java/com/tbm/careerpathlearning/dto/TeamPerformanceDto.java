package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public record TeamPerformanceDto(ScopeOption scope, Period period, Summary summary, Attention attention,
        List<TrendPoint> trend, Distribution distribution, List<Employee> employees, Change yearOnYear) {
    public enum Scope { TEAM, DEPARTMENT }
    public enum Metric { KPI, ATTITUDE, FINAL }
    public enum Comparison { AVAILABLE, NO_PREVIOUS_PERIOD, AMBIGUOUS_PREVIOUS_PERIOD, INCOMPATIBLE_CONFIGURATION,
        NO_COMPARABLE_RESULTS }
    public record ScopeOption(Scope type, Long departmentId, String name) {}
    public record Period(Long id, String name, LocalDate startDate, LocalDate endDate,
            AnnualKpiReviewPeriodStatus status, KpiFrequencyPolicy frequencyPolicy,
            KpiAssessmentModel assessmentModel, AnnualKpiConsolidationMethod consolidationMethod,
            BigDecimal kpiWeight, BigDecimal attitudeWeight) {}
    public record Average(BigDecimal score, int contributingEmployees) {}
    public record Summary(int totalEmployees, int employeesWithResults, Average kpi, Average attitude, Average finalScore) {}
    // Scope progress is unfinished work, NOT an authorisation to act on it. Counts are tasks, not distinct people.
    public record Progress(int kpiSelfAssessments, int kpiSuperiorReviews, int attitudeSelfAssessments,
            int attitudeSuperiorReviews, int returnedIndividualPlans) {}
    public record Actions(int pendingKpiReviews, int pendingAttitudeReviews, int pendingIndividualPlanApprovals,
            int overdueReviews, int returnedIndividualRevisions, List<ActionLink> links) {}
    public record ActionLink(String task, String path, Map<String, String> parameters) {}
    public record Attention(Actions yourActions, Progress scopeProgress) {}
    public record TrendPoint(Period period, Summary summary) {}
    public record Bin(int lowerInclusive, int upper, boolean upperInclusive, int employees) {}
    public record Distribution(Metric metric, List<Bin> bins, int employeesWithResults, int missingResults) {}
    public record Change(BigDecimal percentagePoints, Comparison status, Long previousPeriodId) {}
    public record Employee(UUID employeeId, Long participantId, String name, String departmentName, String roleName,
            StaffPerformanceDto.Scores scores, Change yearOnYear) {}
}
