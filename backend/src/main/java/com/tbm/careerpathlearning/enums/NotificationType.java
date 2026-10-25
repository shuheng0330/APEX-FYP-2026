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
    TRAINER_ACCESS_LAPSED,
    COURSE_TRANSFERRED,
    COURSE_WITHDRAWN,
    COURSE_VERSION_PUBLISHED
}
