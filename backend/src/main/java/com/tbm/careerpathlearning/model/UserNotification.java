package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An in-app notification, shown under the bell until read and also emailed (NFR-016).
 */
@Getter
@Setter
@Entity
@Table(name = "user_notification", indexes = {
        @Index(name = "idx_user_notification_recipient", columnList = "recipient_id, created_at")
})
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    /** A {@link com.tbm.careerpathlearning.enums.NotificationType} name. */
    @Column(name = "type", nullable = false, length = 60)
    private String type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", columnDefinition = "text", nullable = false)
    private String body;

    /** The page in the app the notification opens, such as /my-learning/course/12. */
    @Column(name = "link", length = 300)
    private String link;

    /** Set for scheduled reminders, so a job that runs twice never sends the same one twice. */
    @Column(name = "dedupe_key", length = 200, unique = true)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    /** A {@link com.tbm.careerpathlearning.enums.NotificationEmailStatus} name. */
    @Column(name = "email_status", nullable = false, length = 20)
    private String emailStatus;

    @Column(name = "email_attempts", nullable = false)
    private int emailAttempts;

    @Column(name = "email_error", columnDefinition = "text")
    private String emailError;

    @Column(name = "emailed_at")
    private OffsetDateTime emailedAt;
}
