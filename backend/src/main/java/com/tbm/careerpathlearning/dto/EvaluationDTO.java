package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class EvaluationDTO {
    private Long evaluationId;
    private UUID staffId;
    private String comment;
    private String staffName;
    private String evaluationCycleEndDate;
    private Double overallScore;
    private LocalDateTime createdAt;
    private UUID createdBy;
    private List<RatingDTO> ratings;
}
