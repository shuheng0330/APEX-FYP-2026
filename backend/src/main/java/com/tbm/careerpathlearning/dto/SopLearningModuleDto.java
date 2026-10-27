package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/** A module of a course as the learner sees it, with their progress on it. */
@Data
public class SopLearningModuleDto {
    private Long id;
    private int moduleOrder;
    private String title;
    /**
     * LOCKED: the previous module is not passed yet (FR-11-01). AVAILABLE: ready to study.
     * RETRAINING: the quiz was failed and must be retaken (FR-10-04). PASSED.
     * UNAVAILABLE: the trainer has not approved its material or quiz (FR-08-08).
     */
    private String state;
    private int questionCount;
    /** Questions each attempt presents (FR-08-15). */
    private int quizSize;
    /** Correct answers needed to pass, for example 4 of 5 (FR-11-12). */
    private int passCorrect;
    /** Tries left today, of {@code SopLearningServiceImpl.TRIES_PER_DAY} (FR-11-04). */
    private int triesLeftToday;
    /** The last attempt failed and the material hasn't been opened since, so the quiz can't be retaken yet. */
    private boolean reviewMaterialFirst;
    private int attempts;
    private int failedAttempts;
    private Integer bestScore;
    /** Score of the attempt that passed, which counts towards the final score. */
    private Integer passedScore;
    private OffsetDateTime passedAt;
}
