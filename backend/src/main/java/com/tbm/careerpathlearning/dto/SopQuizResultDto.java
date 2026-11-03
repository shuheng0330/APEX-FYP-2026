package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * The marked quiz. After a fail it holds only the score and what was needed, not which answers were wrong (FR-11-13);
 * each question's result, with the correct answer and explanation, comes once the module is passed.
 */
@Data
public class SopQuizResultDto {
    private int score;
    private int correct;
    private int total;
    private boolean passed;
    private int passMark;
    /** Correct answers this attempt needed (FR-11-12). */
    private int passCorrect;
    /** Tries left today (FR-11-04). */
    private int triesLeftToday;
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
