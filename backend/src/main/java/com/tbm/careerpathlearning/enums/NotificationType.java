package com.tbm.careerpathlearning.enums;

/**
 * What a notification is about. Stored as text rather than as a database enum, so later phases can add
 * types without changing the table.
 */
public enum NotificationType {
    COURSE_ASSIGNED,
    COURSE_ASSIGNMENT_UPDATED,
    MATERIAL_REGENERATED,
    QUIZ_REGENERATED,
    /** A learner failed the same module quiz three times (FR-11-15). */
    QUIZ_FAILED_THREE_TIMES,
    TRAINER_ACCESS_LAPSED,
    COURSE_TRANSFERRED,
    COURSE_WITHDRAWN,
    COURSE_VERSION_PUBLISHED,
    /** A mandatory course's last date to start, or its due date, is 3 days away or less (FR-09-14). */
    COURSE_DUE_SOON,
    /** A mandatory course is past its due date, or was not started by its last date (FR-11-16). */
    COURSE_OVERDUE,
    /** A session the person registered for, or was invited to, was cancelled (FR-12-06). */
    SESSION_CANCELLED,
    /** The date, time, venue or delivery of a session the person registered for, or was invited to, changed. */
    SESSION_CHANGED
}
