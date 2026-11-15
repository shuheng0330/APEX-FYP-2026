package com.tbm.careerpathlearning.enums;

/**
 * What happened to a module's material, recorded in its review history (FR-07-14). Stored as text, so a value added
 * later never meets an old CHECK constraint.
 */
public enum MaterialReviewAction {
    /** Generated from the SOP, by the AI or, when it was unavailable, from the SOP text alone. The history doesn't say which. */
    GENERATED,
    EDITED,
    APPROVED,
    REJECTED,
    REGENERATED,
    /** Copied into a new draft version of the course (FR-07-16). */
    COPIED
}
