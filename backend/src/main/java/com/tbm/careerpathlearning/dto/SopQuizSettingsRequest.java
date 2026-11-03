package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** A module quiz's settings, set by the course's owner on Review Quizzes (FR-08-15). */
@Data
public class SopQuizSettingsRequest {
    /** How many questions each attempt presents: at least 5, at most the questions in the quiz. */
    private Integer questionsPerAttempt;
}
