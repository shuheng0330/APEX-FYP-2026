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
    SESSION_CHANGED,
    /** The person is in a mandatory session's audience and is invited to register (FR-12-14). */
    SESSION_INVITATION,
    /** The person has a seat on a session, by registering or being added by the trainer (FR-13-01). */
    SESSION_REGISTERED,
    /** The session was full, so the person joined its waitlist (FR-13-02). */
    SESSION_WAITLISTED,
    /** A seat freed up and is held for the person for 24 hours (FR-13-07). */
    SESSION_SEAT_OFFERED,
    /** The person did not take up an offered seat in time, so it passed to the next person (FR-13-07). */
    SESSION_OFFER_EXPIRED,
    /** The trainer or HR took the person off a session. */
    SESSION_REMOVED
}
