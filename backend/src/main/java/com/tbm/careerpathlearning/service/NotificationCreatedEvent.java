package com.tbm.careerpathlearning.service;

/** Published when a notification is saved; its email goes out after the transaction commits. */
public record NotificationCreatedEvent(Long notificationId) {
}
