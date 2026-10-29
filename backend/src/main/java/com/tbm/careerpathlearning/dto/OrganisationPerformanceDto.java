package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.dto.TeamPerformanceDto.*;
import com.tbm.careerpathlearning.enums.AnnualAppraisalRecommendation;
import java.util.List;

public record OrganisationPerformanceDto(Period period, int historyYears, Summary summary, Change yearOnYear,
        List<TrendPoint> trend, Distribution distribution, List<Department> departments,
        List<DepartmentTrend> departmentTrends, DecisionDistribution appraisalDistribution) {
    public record Department(Long departmentId, String name, Summary summary, Progress assessmentProgress,
            int pendingHrReview, Change yearOnYear) {}
    public record DepartmentTrend(Long departmentId, String name, List<TrendPoint> periods) {}
    public record DecisionCount(AnnualAppraisalRecommendation decision, int count) {}
    public enum Disclosure { AVAILABLE, NO_DATA, SUPPRESSED }
    public record DecisionSeries(Disclosure disclosure, Integer total, List<DecisionCount> counts) {}
    public record DecisionDistribution(int minimumGroupSize, DecisionSeries superiorRecommendations, DecisionSeries hrFinalDecisions) {}
}
