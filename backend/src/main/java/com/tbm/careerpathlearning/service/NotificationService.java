package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.NotificationListDto;
import com.tbm.careerpathlearning.enums.NotificationType;

import java.util.UUID;

/** Personal notifications saved in the caller's transaction; existing methods also queue email. */
public interface NotificationService {

    /**
     * Notifies a staff member. The notification is saved in the caller's transaction, and its email is sent
     * only after that transaction commits, so nothing is sent for an action that fails.
     *
     * @param link the page in the app the notification opens, or null
     */
    void notify(UUID recipientId, NotificationType type, String title, String body, String link);

    /**
     * Like {@link #notify}, but at most once per key, for scheduled reminders that may run more than once.
     *
     * @return false when a notification with this key already exists
     */
    boolean notifyOnce(String dedupeKey, UUID recipientId, NotificationType type, String title, String body, String link);

    /** Saves an unread notification without queueing email. */
    void notifyInApp(UUID recipientId, NotificationType type, String title, String body, String link);

    /** Saves an in-app-only notification at most once per nonblank key, including concurrent requests. */
    boolean notifyInAppOnce(String dedupeKey, UUID recipientId, NotificationType type, String title, String body, String link);

    /** The person's unread count and newest notifications. */
    NotificationListDto mine(UUID userId);

    long unreadCount(UUID userId);

    /** Marks one of the person's own notifications as read. */
    void markRead(Long notificationId, UUID userId);

    void markAllRead(UUID userId);
}
