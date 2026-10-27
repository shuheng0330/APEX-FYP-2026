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
    SESSION_RESTORED
}
