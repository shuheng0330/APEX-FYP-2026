package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.*;
import lombok.Data;
import java.time.*;
import java.util.*;

@Data
public class AttitudeAssessmentDto {
    private Long id;
    private Long participantId;
    private String employeeName;
    private String roleName;
    private String departmentName;
    private Long reviewPeriodId;
    private String reviewPeriodName;
    private AnnualKpiReviewPeriodStatus reviewPeriodStatus;
    private Long configurationId;
    private String configurationName;
    private AttitudeEvaluationFormat evaluationFormat;
    private AttitudeAssessmentStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime submittedAt;
    private UUID submittedBy;
    private UUID submittedToSuperiorId;
    private Boolean submittedLate;
    private LocalDate selfAssessmentDeadline;
    private LocalDate superiorEvaluationDeadline;
    private boolean available;
    private String availabilityTitle;
    private String availabilityMessage;
    private boolean canSaveDraft;
    private boolean canSubmit;
    private boolean overdue;
    private List<String> submissionBlockers=new ArrayList<>();
    private List<AttitudeConfigurationRequest.Rating> ratingDefinitions=new ArrayList<>();
    private List<Item> items=new ArrayList<>();
    @Data public static class Item {
        private Long id;
        private Long criterionId;
        private String name;
        private String description;
        private AttitudeCriterionType criterionType;
        private int displayOrder;
        private Integer selfPoint;
        private String selfComment;
    }
}
