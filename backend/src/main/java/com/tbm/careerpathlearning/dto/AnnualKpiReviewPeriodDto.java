package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class AnnualKpiReviewPeriodDto {
    private Long id;
    private String referenceNumber;
    private AnnualKpiReviewPeriodStatus status;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal companyKpiWeight;
    private BigDecimal departmentKpiWeight;
    private BigDecimal individualKpiWeight;
    private BigDecimal kpiPerformanceWeight;
    private BigDecimal attitudeEvaluationWeight;
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
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime openedAt;
    private OffsetDateTime closedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private List<RoleConfiguration> roleConfigurations = new ArrayList<>();
    private List<Checkpoint> checkpoints = new ArrayList<>();

    @Data
    public static class RoleConfiguration {
        private Long roleId;
        private String roleName;
        private String departmentName;
        private ReviewFrequency reviewFrequency;
    }

    @Data
    public static class Checkpoint {
        private Long id;
        private ReviewFrequency reviewFrequency;
        private Integer sequenceNumber;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDate selfAssessmentDeadline;
        private LocalDate superiorAssessmentDeadline;
    }
}
