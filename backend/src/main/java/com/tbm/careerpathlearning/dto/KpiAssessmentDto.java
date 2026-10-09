package com.tbm.careerpathlearning.dto;
import com.tbm.careerpathlearning.enums.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
@Data
public class KpiAssessmentDto {
    private Long id;
    private Long participantId;
    private String employeeName;
    private String roleName;
    private Long reviewPeriodId;
    private String reviewPeriodName;
    private AnnualKpiReviewPeriodStatus reviewPeriodStatus;
    private KpiAssessmentCheckpointDto checkpoint;
    private KpiAssessmentStatus status;
    private List<KpiAssessmentItemDto> items=new ArrayList<>();
    private ReviewPeriodEmployeeLevelConfigurationDto kpiAllocation;
    private List<KpiLevel> missingLevels=new ArrayList<>();
    private List<String> submissionBlockers=new ArrayList<>();
    private boolean canSaveDraft;
    private boolean canSubmit;
    private boolean overdue;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime submittedAt;
    private UUID submittedBy;
    private UUID submittedToSuperiorId;
    private Boolean submittedLate;
    private OffsetDateTime reviewedAt;
    private UUID reviewedBy;
    private Boolean reviewedLate;
    private BigDecimal checkpointScore;
}
