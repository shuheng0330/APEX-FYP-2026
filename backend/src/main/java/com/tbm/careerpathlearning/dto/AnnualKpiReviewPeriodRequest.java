package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AnnualKpiConsolidationMethod;
import com.tbm.careerpathlearning.enums.ReviewFrequency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class AnnualKpiReviewPeriodRequest {
    @Size(max = 255)
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    // Reject obsolete clients explicitly rather than silently discarding global weights.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private BigDecimal companyKpiWeight;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private BigDecimal departmentKpiWeight;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private BigDecimal individualKpiWeight;
    @Valid
    private List<@NotNull ReviewPeriodEmployeeLevelConfigurationDto> employeeLevelConfigurations;

    @JsonIgnore
    @AssertTrue(message = "Configure KPI weightages per Employee Level; global KPI weights are no longer supported")
    public boolean isGlobalKpiWeightsAbsent() {
        return companyKpiWeight == null && departmentKpiWeight == null && individualKpiWeight == null;
    }
    private BigDecimal kpiPerformanceWeight = new BigDecimal("50.00");
    private BigDecimal attitudeEvaluationWeight = new BigDecimal("50.00");
    private AnnualKpiConsolidationMethod annualKpiConsolidationMethod;
    private LocalDate companyKpiCreationDeadline;
    private LocalDate departmentKpiCreationDeadline;
    private LocalDate individualKpiSubmissionDeadline;
    private LocalDate individualKpiApprovalDeadline;
    private Integer selfAssessmentDaysAfterCheckpoint;
    private Integer superiorAssessmentDaysAfterSelfDeadline;
    private LocalDate attitudeSelfAssessmentDeadline;
    private LocalDate superiorAttitudeEvaluationDeadline;
    private LocalDate appraisalRecommendationDeadline;
    private LocalDate hrFinalisationDeadline;
    @Valid
    @NotNull
    private List<@NotNull RoleFrequency> roleConfigurations = new ArrayList<>();

    @Data
    public static class RoleFrequency {
        @NotNull
        @Positive
        private Long roleId;
        private ReviewFrequency reviewFrequency;
    }
}
