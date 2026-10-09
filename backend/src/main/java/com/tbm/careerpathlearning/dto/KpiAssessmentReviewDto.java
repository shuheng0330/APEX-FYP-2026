package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class KpiAssessmentReviewDto {
    private Long id;
    private UUID employeeId;
    private String employeeName;
    private String roleName;
    private String departmentName;
    private Long reviewPeriodId;
    private String reviewPeriodName;
    private AnnualKpiReviewPeriodStatus reviewPeriodStatus;
    private KpiAssessmentCheckpointDto checkpoint;
    private KpiAssessmentStatus status;
    private OffsetDateTime submittedAt;
    private Boolean submittedLate;
    private OffsetDateTime reviewedAt;
    private Boolean reviewedLate;
    private BigDecimal checkpointScore;
    private boolean canReview;
    private boolean superiorDraftSaved;
    private boolean superiorOverdue;
}
