package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class EvaluationRatingsDto {

    private Long evaluationRatingId;

    private EvaluationDTO evaluation;

    private CompetencyDto competency;

    private Integer rating;

    private LocalDate createdAt;
}
