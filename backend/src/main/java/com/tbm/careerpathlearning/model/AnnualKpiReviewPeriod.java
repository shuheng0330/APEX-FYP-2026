package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.AnnualKpiConsolidationMethod;
import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "annual_kpi_review_period", uniqueConstraints = {
        @UniqueConstraint(name = "uq_annual_kpi_period_name", columnNames = "name"),
        @UniqueConstraint(name = "uq_annual_kpi_period_reference", columnNames = "reference_number")
}, indexes = @Index(name = "idx_annual_kpi_period_status_dates", columnList = "status,start_date,end_date"))
public class AnnualKpiReviewPeriod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_number", nullable = false, updatable = false, length = 36)
    private String referenceNumber = UUID.randomUUID().toString();

    @Column(length = 255)
    private String name;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnualKpiReviewPeriodStatus status = AnnualKpiReviewPeriodStatus.DRAFT;

    @Column(name = "kpi_performance_weight", precision = 5, scale = 2)
    private BigDecimal kpiPerformanceWeight = new BigDecimal("50.00");

    @Column(name = "attitude_evaluation_weight", precision = 5, scale = 2)
    private BigDecimal attitudeEvaluationWeight = new BigDecimal("50.00");

    @Enumerated(EnumType.STRING)
    @Column(name = "annual_kpi_consolidation_method", length = 30)
    private AnnualKpiConsolidationMethod annualKpiConsolidationMethod;

    @Column(name = "company_kpi_creation_deadline")
    private LocalDate companyKpiCreationDeadline;

    @Column(name = "department_kpi_creation_deadline")
    private LocalDate departmentKpiCreationDeadline;

    @Column(name = "individual_kpi_submission_deadline")
    private LocalDate individualKpiSubmissionDeadline;

    @Column(name = "individual_kpi_approval_deadline")
    private LocalDate individualKpiApprovalDeadline;

    @Column(name = "self_assessment_days_after_checkpoint")
    private Integer selfAssessmentDaysAfterCheckpoint;

    @Column(name = "superior_assessment_days_after_self_deadline")
    private Integer superiorAssessmentDaysAfterSelfDeadline;

    @Column(name = "attitude_self_assessment_deadline")
    private LocalDate attitudeSelfAssessmentDeadline;

    @Column(name = "superior_attitude_evaluation_deadline")
    private LocalDate superiorAttitudeEvaluationDeadline;

    @Column(name = "appraisal_recommendation_deadline")
    private LocalDate appraisalRecommendationDeadline;

    @Column(name = "hr_finalisation_deadline")
    private LocalDate hrFinalisationDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "opened_at")
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @PrePersist
    void initialiseTimestamps() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = OffsetDateTime.now();
    }
}
