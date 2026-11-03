package com.tbm.careerpathlearning.dto;
import com.tbm.careerpathlearning.enums.*;
import lombok.Data;
import java.time.LocalDate;
@Data
public class KpiAssessmentCheckpointDto {
    private Long id;
    private ReviewFrequency reviewFrequency;
    private Integer sequenceNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate availableFrom;
    private LocalDate selfAssessmentDeadline;
    private LocalDate superiorAssessmentDeadline;
    private boolean available;
    private boolean overdue;
    private Long assessmentId;
    private KpiAssessmentStatus assessmentStatus;
}
