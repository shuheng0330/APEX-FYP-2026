package com.tbm.careerpathlearning.enums;

/**
 * What happened to a module's quiz, recorded in its review history (FR-08-09).
 */
public enum QuizReviewAction {
    APPROVED,
    REJECTED,
    REGENERATED,
    EDITED,
    /** Copied into a new draft version of the course (FR-07-16). */
    COPIED
}
