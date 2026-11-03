package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.QuizDifficulty;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** A quiz question as sent to the learner: deliberately without the answer or explanation. */
@Data
public class SopLearningQuestionDto {
    private Long id;
    private QuizQuestionType type;
    private String question;
    private List<String> options = new ArrayList<>();
    private QuizDifficulty difficulty;
}
