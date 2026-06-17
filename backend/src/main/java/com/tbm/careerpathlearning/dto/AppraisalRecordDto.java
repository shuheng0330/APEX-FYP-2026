package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AppraisalCategory;
import com.tbm.careerpathlearning.enums.AppraisalDecisionType;
import com.tbm.careerpathlearning.enums.AppraisalStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AppraisalRecordDto {
    private UUID id;
    private UUID staffId;
    private String staffName;
    private String departmentName;
    private String roleName;
    private UUID managerId;
    private String managerName;
    private Long evaluationCycleId;
    private String evaluationCycleEndDate;
    private Integer reviewPeriodYears;
    private AppraisalDecisionType decisionType;
    private BigDecimal promotionReadinessScore;
    private BigDecimal salaryReadinessScore;
    private AppraisalCategory promotionSystemCategory;
    private AppraisalCategory salarySystemCategory;
    private AppraisalCategory promotionManagerCategory;
    private AppraisalCategory salaryManagerCategory;
    private String promotionManagerOverrideReason;
    private String salaryManagerOverrideReason;
    private String managerComment;
    private String aiInsight;
    private AppraisalStatus status;
    private UUID hrReviewerId;
    private String hrReviewerName;
    private AppraisalCategory promotionHrOverrideCategory;
    private String promotionHrOverrideReason;
    private AppraisalCategory salaryHrOverrideCategory;
    private String salaryHrOverrideReason;
    private AppraisalCategory promotionEffectiveCategory;
    private AppraisalCategory salaryEffectiveCategory;
    private String hrReturnReason;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
}
