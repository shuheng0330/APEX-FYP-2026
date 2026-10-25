package com.tbm.careerpathlearning.enums;

/** What happened to a notification's email (NFR-016). Stored as text, like {@link NotificationType}. */
public enum NotificationEmailStatus {
    /** Waiting to be sent after the action that created it is saved. */
    PENDING,
    SENT,
    /** Every attempt failed; the in-app notification is unaffected. */
    FAILED,
    /** In-app-only delivery, email is switched off, or development has no test inbox. */
    SKIPPED,
    /** The recipient has no email address. */
    NO_ADDRESS
}
