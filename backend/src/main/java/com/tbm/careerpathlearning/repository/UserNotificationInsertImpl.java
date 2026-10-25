package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.UserNotification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;

public class UserNotificationInsertImpl implements UserNotificationInsert {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Long insertOnce(UserNotification notification) {
        // Use Hibernate's connection so conflicts never abort or escape the caller's transaction.
        return entityManager.unwrap(Session.class).doReturningWork(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO user_notification
                        (recipient_id, type, title, body, link, dedupe_key, created_at, email_status, email_attempts)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                    ON CONFLICT (dedupe_key) DO NOTHING
                    RETURNING id
                    """)) {
                statement.setObject(1, notification.getRecipientId());
                statement.setString(2, notification.getType());
                statement.setString(3, notification.getTitle());
                statement.setString(4, notification.getBody());
                statement.setString(5, notification.getLink());
                statement.setString(6, notification.getDedupeKey());
                statement.setObject(7, notification.getCreatedAt());
                statement.setString(8, notification.getEmailStatus());
                try (var result = statement.executeQuery()) {
                    return result.next() ? result.getLong(1) : null;
                }
            }
        });
    }
}
