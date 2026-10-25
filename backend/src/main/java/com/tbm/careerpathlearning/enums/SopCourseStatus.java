package com.tbm.careerpathlearning.enums;

/**
 * Where a course is in its life (NFR-010: Draft, Live, Archived). Every course starts Live; versions (Phase 2.5)
 * bring Draft and Superseded. All four values exist from the start so the column never needs another one.
 */
public enum SopCourseStatus {
    /** The version staff are assigned, join and take. */
    LIVE,
    /** A new version being prepared; staff keep taking the live one until it is published. */
    DRAFT,
    /** An earlier version replaced by a newer one; its records are kept. */
    SUPERSEDED,
    /**
     * Retired after staff had enrolled, so it could not be deleted (FR-06-14): hidden from the Learning Portal,
     * Assign Training and the review lists, with its certificates, quiz attempts and scores kept.
     */
    ARCHIVED
}
