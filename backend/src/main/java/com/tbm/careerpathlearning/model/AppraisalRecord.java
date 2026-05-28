package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.AppraisalCategory;
import com.tbm.careerpathlearning.enums.AppraisalDecisionType;
import com.tbm.careerpathlearning.enums.AppraisalStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "appraisal_record")
public class AppraisalRecord {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private Staff manager;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluation_cycle_id")
    private EvaluationCycle evaluationCycle;

    private Integer reviewPeriodYears;

    @Enumerated(EnumType.STRING)
    private AppraisalDecisionType decisionType;

    private BigDecimal promotionReadinessScore;

    private BigDecimal salaryReadinessScore;

    @Enumerated(EnumType.STRING)
    private AppraisalCategory promotionSystemCategory;

    @Enumerated(EnumType.STRING)
    private AppraisalCategory salarySystemCategory;

    @Enumerated(EnumType.STRING)
    private AppraisalCategory promotionFinalCategory;

    @Enumerated(EnumType.STRING)
    private AppraisalCategory salaryFinalCategory;

    private String promotionOverrideReason;

    private String salaryOverrideReason;

    private String managerComment;

    private String aiInsight;

    @Enumerated(EnumType.STRING)
    private AppraisalStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hr_reviewer_id")
    private Staff hrReviewer;

    @Enumerated(EnumType.STRING)
    private AppraisalCategory hrOverrideCategory;

    private String hrOverrideReason;

    private String hrReturnReason;

    private LocalDateTime submittedAt;

    private LocalDateTime approvedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private UUID createdBy;

    private UUID updatedBy;

    @PrePersist
    public void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = AppraisalStatus.DRAFT;
        }
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
