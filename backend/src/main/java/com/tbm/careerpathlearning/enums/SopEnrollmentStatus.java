package com.tbm.careerpathlearning.enums;

/**
 * A staff member's progress through an SOP course they are enrolled in.
 */
public enum SopEnrollmentStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    /** Taken off a course archived before they finished it; their progress is kept (FR-06-15). */
    WITHDRAWN
}
