package com.tbm.careerpathlearning.enums;

/**
 * What happened to a module's material, recorded in its review history (FR-07-14). Stored as text, so a value added
 * later never meets an old CHECK constraint.
 */
public enum MaterialReviewAction {
    /** Written by the AI from the SOP, or built from the SOP text when the AI was unavailable (FR-06-09). */
    GENERATED,
    EDITED,
    APPROVED,
    REJECTED,
    REGENERATED,
    /** Copied into a new draft version of the course (FR-07-16). */
    COPIED
}
