package com.tbm.careerpathlearning.enums;

/**
 * The kind of record a training audit log entry is about. Stored as text, like {@link TrainingAuditAction}.
 */
public enum TrainingAuditTarget {
    STAFF,
    COURSE,
    /** A training session (FR-12); the target id is the session's id. */
    SESSION
}
