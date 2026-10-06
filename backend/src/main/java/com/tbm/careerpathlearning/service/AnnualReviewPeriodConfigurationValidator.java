package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class AnnualReviewPeriodConfigurationValidator {
    public void validateDraft(AnnualKpiReviewPeriod period) {
        Objects.requireNonNull(period, "Review period is required");
        if (period.getName() != null && period.getName().isBlank()) {
            throw new IllegalArgumentException("Review period name cannot be blank");
        }
        if (period.getStartDate() != null && period.getEndDate() != null
                && !period.getStartDate().isBefore(period.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        Stream.of(period.getCompanyKpiWeight(), period.getDepartmentKpiWeight(), period.getIndividualKpiWeight(),
                        period.getKpiPerformanceWeight(), period.getAttitudeEvaluationWeight())
                .filter(Objects::nonNull).forEach(this::validateWeight);
        validateOffset(period.getSelfAssessmentDaysAfterCheckpoint());
        validateOffset(period.getSuperiorAssessmentDaysAfterSelfDeadline());
    }

    public void validateForScheduling(AnnualKpiReviewPeriod period) {
        validateDraft(period);
        if (period.getName() == null || period.getStartDate() == null || period.getEndDate() == null
                || period.getAnnualKpiConsolidationMethod() == null
                || period.getSelfAssessmentDaysAfterCheckpoint() == null
                || period.getSuperiorAssessmentDaysAfterSelfDeadline() == null) {
            throw new IllegalArgumentException("Review period configuration is incomplete");
        }
        requireTotal(period.getCompanyKpiWeight(), period.getDepartmentKpiWeight(), period.getIndividualKpiWeight());
        requireTotal(period.getKpiPerformanceWeight(), period.getAttitudeEvaluationWeight());
        Stream.of(period.getCompanyKpiCreationDeadline(), period.getDepartmentKpiCreationDeadline(),
                        period.getIndividualKpiSubmissionDeadline(), period.getIndividualKpiApprovalDeadline())
                .filter(Objects::nonNull).forEach(deadline -> {
                    if (deadline.isAfter(period.getStartDate())) {
                        throw new IllegalArgumentException("KPI setup deadlines must not be after the period start date");
                    }
                });
        validateOrder(period.getAttitudeSelfAssessmentDeadline(), period.getSuperiorAttitudeEvaluationDeadline());
        validateOrder(period.getAppraisalRecommendationDeadline(), period.getHrFinalisationDeadline());
    }

    private void validateWeight(BigDecimal weight) {
        if (weight.signum() < 0 || weight.compareTo(new BigDecimal("100")) > 0
                || Math.max(weight.stripTrailingZeros().scale(), 0) > 2) {
            throw new IllegalArgumentException("Weights must be between 0 and 100 with at most two decimal places");
        }
    }

    private void validateOffset(Integer days) {
        if (days != null && days <= 0) {
            throw new IllegalArgumentException("Assessment deadline offsets must be positive");
        }
    }

    private void requireTotal(BigDecimal... weights) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal weight : weights) {
            if (weight == null) throw new IllegalArgumentException("All weights are required before scheduling");
            total = total.add(weight);
        }
        if (total.compareTo(new BigDecimal("100")) != 0) {
            throw new IllegalArgumentException("Configured weights must total 100 percent");
        }
    }

    private void validateOrder(LocalDate first, LocalDate second) {
        if (first != null && second != null && !first.isBefore(second)) {
            throw new IllegalArgumentException("The subsequent deadline must be after the preceding deadline");
        }
    }
}
