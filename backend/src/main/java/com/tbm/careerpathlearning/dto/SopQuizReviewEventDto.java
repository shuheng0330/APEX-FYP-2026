package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.QuizReviewAction;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class SopQuizReviewEventDto {
    private Long id;
    private QuizReviewAction action;
    private String detail;
    /** Name of the staff member who acted; null when the AI regenerated the quiz. */
    private String actorName;
    private OffsetDateTime createdAt;
}
