package com.tbm.careerpathlearning.enums;

/**
 * Actions recorded in the training audit log (NFR-007). Stored as text rather than as a database enum,
 * so later phases can add actions without changing the table.
 */
public enum TrainingAuditAction {
    TRAINER_ACCESS_GRANTED,
    TRAINER_ACCESS_END_DATE_CHANGED,
    TRAINER_ACCESS_REVOKED,
    TRAINER_ACCESS_LAPSED,
    COURSE_TRANSFERRED,
    COURSE_ARCHIVED,
    COURSE_RESTORED,
    COURSE_DRAFT_CREATED,
    COURSE_VERSION_PUBLISHED,
    COURSE_DRAFT_DISCARDED,
    SESSION_CANCELLED,
    SESSION_DELETED,
    SESSION_ARCHIVED,
    SESSION_RESTORED,
    /** The trainer's final confirmation of a session's attendance (FR-14-09). */
    ATTENDANCE_SUBMITTED,
    /** HR recorded or changed attendance after it was submitted or after the 7 days, with a reason (FR-14-01, FR-14-09). */
    ATTENDANCE_CHANGED,
    EVIDENCE_REMOVED,
    /** The session's trainer or HR downloaded its report or attendance file, with IC or passport numbers (decision D). */
    IC_NUMBERS_EXPORTED,
    /** Someone asked for Trainer access after attending a Product Training session (FR-15-06); target STAFF. */
    TRAINER_ACCESS_REQUESTED,
    /** Course categories (FR-19-01 to FR-19-04); target CATEGORY. */
    CATEGORY_CREATED,
    CATEGORY_CHANGED,
    CATEGORY_RETIRED,
    CATEGORY_RESTORED,
    CATEGORY_DELETED
}
