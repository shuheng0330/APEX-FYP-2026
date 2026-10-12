package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * The marked quiz. Correct answers and explanations are only revealed once the module is passed,
 * so a failed learner reviews the material instead of copying answers (FR-11-04).
 */
@Data
public class SopQuizResultDto {
    private int score;
    private int correct;
    private int total;
    private boolean passed;
    private int passMark;
    /** Attempts on this module so far, including this one (FR-10-05). */
    private int attempts;
    private String moduleState;
    private boolean courseCompleted;
    private Integer finalScore;
    /** The module to study next, if one is now unlocked. */
    private Long nextModuleId;
    private List<QuestionResult> questions = new ArrayList<>();

    @Data
    public static class QuestionResult {
        private Long questionId;
        private boolean correct;
        private String givenAnswer;
        /** Only when the module is passed. */
        private String correctAnswer;
        /** Only when the module is passed. */
        private String explanation;
    }
}
