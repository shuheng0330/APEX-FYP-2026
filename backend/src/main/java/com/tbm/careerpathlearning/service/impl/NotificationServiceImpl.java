package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.NotificationDto;
import com.tbm.careerpathlearning.dto.NotificationListDto;
import com.tbm.careerpathlearning.enums.NotificationEmailStatus;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.UserNotification;
import com.tbm.careerpathlearning.repository.UserNotificationRepository;
import com.tbm.careerpathlearning.service.NotificationCreatedEvent;
import com.tbm.careerpathlearning.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired private UserNotificationRepository userNotificationRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;
    @Autowired private Clock clock;

    @Override
    @Transactional
    public void notify(UUID recipientId, NotificationType type, String title, String body, String link) {
        create(null, recipientId, type, title, body, link, true);
    }

    @Override
    @Transactional
    public boolean notifyOnce(String dedupeKey, UUID recipientId, NotificationType type, String title, String body,
                              String link) {
        requireKey(dedupeKey);
        return create(dedupeKey, recipientId, type, title, body, link, true);
    }

    @Override
    @Transactional
    public void notifyInApp(UUID recipientId, NotificationType type, String title, String body, String link) {
        create(null, recipientId, type, title, body, link, false);
    }

    @Override
    @Transactional
    public boolean notifyInAppOnce(String dedupeKey, UUID recipientId, NotificationType type, String title,
                                   String body, String link) {
        requireKey(dedupeKey);
        return create(dedupeKey, recipientId, type, title, body, link, false);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationListDto mine(UUID userId) {
        NotificationListDto dto = new NotificationListDto();
        dto.setUnread(userNotificationRepository.countByRecipientIdAndReadAtIsNull(userId));
        dto.setItems(userNotificationRepository.findTop30ByRecipientIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(NotificationServiceImpl::toDto)
                .toList());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return userNotificationRepository.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Override
    @Transactional
    public void markRead(Long notificationId, UUID userId) {
        UserNotification n = userNotificationRepository.findById(notificationId)
                .filter(x -> x.getRecipientId().equals(userId))
                .orElseThrow(() -> new BadRequestException("Notification not found."));
        if (n.getReadAt() == null) {
            n.setReadAt(OffsetDateTime.now(clock));
            userNotificationRepository.save(n);
        }
    }

    @Override
    @Transactional
    public void markAllRead(UUID userId) {
        userNotificationRepository.markAllRead(userId, OffsetDateTime.now(clock));
    }

    private boolean create(String dedupeKey, UUID recipientId, NotificationType type, String title, String body,
                           String link, boolean email) {
        UserNotification n = new UserNotification();
        n.setRecipientId(recipientId);
        n.setType(type.name());
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        n.setDedupeKey(dedupeKey);
        n.setCreatedAt(OffsetDateTime.now(clock));
        n.setEmailStatus((email ? NotificationEmailStatus.PENDING : NotificationEmailStatus.SKIPPED).name());
        if (dedupeKey == null) {
            userNotificationRepository.save(n);
        } else {
            Long id = userNotificationRepository.insertOnce(n);
            if (id == null) {
                return false;
            }
            n.setId(id);
        }
        if (email) {
            eventPublisher.publishEvent(new NotificationCreatedEvent(n.getId()));
        }
        return true;
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 200) {
            throw new BadRequestException("A notification key of 1 to 200 characters is required.");
        }
    }

    private static NotificationDto toDto(UserNotification n) {
        NotificationDto dto = new NotificationDto();
        dto.setId(n.getId());
        dto.setType(n.getType());
        dto.setTitle(n.getTitle());
        dto.setBody(n.getBody());
        dto.setLink(n.getLink());
        dto.setCreatedAt(n.getCreatedAt());
        dto.setRead(n.getReadAt() != null);
        return dto;
    }
}
