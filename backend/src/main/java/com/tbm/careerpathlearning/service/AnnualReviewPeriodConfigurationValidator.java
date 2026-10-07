package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import com.tbm.careerpathlearning.model.ReviewPeriodEmployeeLevelConfiguration;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.Objects;
import java.util.stream.Stream;

@Component
public class AnnualReviewPeriodConfigurationValidator {
    public void validateDraft(AnnualKpiReviewPeriod period) {
        Objects.requireNonNull(period, "Review period is required");
        if (period.getName() != null && (period.getName().isBlank() || period.getName().length() > 255)) {
            throw new IllegalArgumentException("Review period name must contain 1 to 255 characters");
        }
        if (period.getStartDate() != null && period.getEndDate() != null
                && !period.getStartDate().isBefore(period.getEndDate())) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        Stream.of(period.getKpiPerformanceWeight(), period.getAttitudeEvaluationWeight())
                .filter(Objects::nonNull).forEach(this::validateWeight);
        validateOffset(period.getSelfAssessmentDaysAfterCheckpoint());
        validateOffset(period.getSuperiorAssessmentDaysAfterSelfDeadline());
        validateDeadlines(period);
    }

    public void validateForScheduling(AnnualKpiReviewPeriod period) {
        validateDraft(period);
        if (period.getName() == null || period.getStartDate() == null || period.getEndDate() == null
                || period.getAnnualKpiConsolidationMethod() == null
                || period.getSelfAssessmentDaysAfterCheckpoint() == null
                || period.getSuperiorAssessmentDaysAfterSelfDeadline() == null) {
            throw new IllegalArgumentException("Review period configuration is incomplete");
        }
        requireTotal(period.getKpiPerformanceWeight(), period.getAttitudeEvaluationWeight());
    }

    public void validateForPublication(AnnualKpiReviewPeriod period) {
        validateForScheduling(period);
        if (Stream.of(period.getKpiSetupDeadline(),
                        period.getAttitudeSelfAssessmentDeadline(), period.getSuperiorAttitudeEvaluationDeadline(),
                        period.getAppraisalRecommendationDeadline(), period.getHrFinalisationDeadline())
                .anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("All KPI setup, attitude and appraisal deadlines are required before publishing");
        }
        if (period.getAttitudeSelfAssessmentDeadline().isBefore(period.getStartDate())) {
            throw new IllegalArgumentException("Attitude self-assessment deadline cannot precede the period start date");
        }
        try {
            LocalDate finalSuperiorDeadline = period.getEndDate()
                    .plusDays(period.getSelfAssessmentDaysAfterCheckpoint())
                    .plusDays(period.getSuperiorAssessmentDaysAfterSelfDeadline());
            if (period.getAppraisalRecommendationDeadline().isBefore(finalSuperiorDeadline)
                    || period.getAppraisalRecommendationDeadline().isBefore(period.getSuperiorAttitudeEvaluationDeadline())) {
                throw new IllegalArgumentException("Set the Appraisal Recommendation Deadline after the final KPI assessment and Superior Attitude Evaluation have been completed.");
            }
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("Assessment deadline offsets produce an invalid date", ex);
        }
    }

    private void validateDeadlines(AnnualKpiReviewPeriod period) {
        Stream.of(period.getKpiSetupDeadline())
                .filter(Objects::nonNull).forEach(deadline -> {
                    if (period.getStartDate() != null && deadline.isAfter(period.getStartDate())) {
                        throw new IllegalArgumentException("KPI Setup Deadline must be on or before the period Start Date");
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

    public void validateLevelWeights(ReviewPeriodEmployeeLevelConfiguration configuration, boolean publish) {
        Stream.of(configuration.getCompanyKpiWeight(), configuration.getDepartmentKpiWeight(), configuration.getIndividualKpiWeight())
                .filter(Objects::nonNull).forEach(this::validateWeight);
        if (publish) requireTotal(configuration.getCompanyKpiWeight(), configuration.getDepartmentKpiWeight(), configuration.getIndividualKpiWeight());
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
