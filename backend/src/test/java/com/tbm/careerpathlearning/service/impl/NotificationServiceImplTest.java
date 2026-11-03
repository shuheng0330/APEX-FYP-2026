package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.NotificationListDto;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.UserNotification;
import com.tbm.careerpathlearning.repository.UserNotificationRepository;
import com.tbm.careerpathlearning.service.NotificationCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private UserNotificationRepository userNotificationRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationServiceImpl service;

    private final Instant now = Instant.parse("2026-10-12T02:00:00Z");
    private final UUID aisyah = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(now, ClockConfig.ZONE));
    }

    @Test
    @DisplayName("A notification is saved unread with its email pending, then announced for emailing")
    void notifySavesAndAnnounces() {
        when(userNotificationRepository.save(any())).thenAnswer(inv -> {
            UserNotification n = inv.getArgument(0);
            n.setId(7L);
            return n;
        });

        service.notify(aisyah, NotificationType.COURSE_ASSIGNED, "New training: Cash Handling",
                "Complete it by 26 Oct 2026.", "/my-learning/course/1");

        ArgumentCaptor<UserNotification> saved = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationRepository).save(saved.capture());
        UserNotification n = saved.getValue();
        assertEquals(aisyah, n.getRecipientId());
        assertEquals("COURSE_ASSIGNED", n.getType());
        assertEquals("New training: Cash Handling", n.getTitle());
        assertEquals("/my-learning/course/1", n.getLink());
        assertNull(n.getReadAt());
        assertEquals("PENDING", n.getEmailStatus());
        assertEquals(now, n.getCreatedAt().toInstant());
        verify(eventPublisher).publishEvent(new NotificationCreatedEvent(7L));
    }

    @Test
    @DisplayName("A keyed notification is sent only once, however many times it is asked for")
    void notifyOnce() {
        when(userNotificationRepository.insertOnce(any())).thenReturn(7L, (Long) null);

        assertTrue(service.notifyOnce("due-3d:enrollment:5", aisyah, NotificationType.COURSE_ASSIGNED, "t", "b", null));
        assertFalse(service.notifyOnce("due-3d:enrollment:5", aisyah, NotificationType.COURSE_ASSIGNED, "t", "b", null));

        verify(userNotificationRepository, times(2)).insertOnce(any());
        verify(userNotificationRepository, never()).save(any());
        verify(userNotificationRepository, never()).existsByDedupeKey(any());
        verify(eventPublisher).publishEvent(new NotificationCreatedEvent(7L));
    }

    @Test
    void inAppNotificationIsUnreadAndNeverQueuesEmail() {
        service.notifyInApp(aisyah, NotificationType.COURSE_ASSIGNED, "A task is ready", "Open to review.", "/my-learning");

        var saved = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationRepository).save(saved.capture());
        assertEquals("SKIPPED", saved.getValue().getEmailStatus());
        assertNull(saved.getValue().getReadAt());
        assertEquals(0, saved.getValue().getEmailAttempts());
        assertEquals(aisyah, saved.getValue().getRecipientId());
        assertEquals(now, saved.getValue().getCreatedAt().toInstant());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void inAppOnceUsesAtomicInsertionWithoutPublishingMailEvents() {
        when(userNotificationRepository.insertOnce(any())).thenReturn(8L, (Long) null);

        assertTrue(service.notifyInAppOnce("event:1:recipient", aisyah, NotificationType.COURSE_ASSIGNED, "t", "b", null));
        assertFalse(service.notifyInAppOnce("event:1:recipient", aisyah, NotificationType.COURSE_ASSIGNED, "t", "b", null));

        var inserted = ArgumentCaptor.forClass(UserNotification.class);
        verify(userNotificationRepository, times(2)).insertOnce(inserted.capture());
        assertTrue(inserted.getAllValues().stream().allMatch(n -> "SKIPPED".equals(n.getEmailStatus())));
        assertEquals("event:1:recipient", inserted.getValue().getDedupeKey());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void onceMethodsRejectMissingBlankAndOversizedKeysBeforeWriting() {
        for (String key : new String[] { null, " ", "x".repeat(201) }) {
            assertThrows(BadRequestException.class, () -> service.notifyOnce(key, aisyah,
                    NotificationType.COURSE_ASSIGNED, "t", "b", null));
            assertThrows(BadRequestException.class, () -> service.notifyInAppOnce(key, aisyah,
                    NotificationType.COURSE_ASSIGNED, "t", "b", null));
        }
        verifyNoInteractions(userNotificationRepository, eventPublisher);
    }

    @Test
    @DisplayName("The bell gets the unread count and the newest notifications")
    void mine() {
        UserNotification read = notification(2L, aisyah);
        read.setReadAt(OffsetDateTime.now(Clock.fixed(now, ClockConfig.ZONE)));
        when(userNotificationRepository.countByRecipientIdAndReadAtIsNull(aisyah)).thenReturn(1L);
        when(userNotificationRepository.findTop30ByRecipientIdOrderByCreatedAtDescIdDesc(aisyah))
                .thenReturn(List.of(read, notification(1L, aisyah)));

        NotificationListDto dto = service.mine(aisyah);

        assertEquals(1, dto.getUnread());
        assertEquals(List.of(2L, 1L), dto.getItems().stream().map(i -> i.getId()).toList());
        assertTrue(dto.getItems().get(0).isRead());
        assertFalse(dto.getItems().get(1).isRead());
    }

    @Test
    @DisplayName("People can mark only their own notifications as read")
    void markReadOwnOnly() {
        UserNotification mine = notification(1L, aisyah);
        UserNotification someoneElses = notification(2L, UUID.randomUUID());
        when(userNotificationRepository.findById(1L)).thenReturn(Optional.of(mine));
        when(userNotificationRepository.findById(2L)).thenReturn(Optional.of(someoneElses));

        service.markRead(1L, aisyah);

        assertNotNull(mine.getReadAt());
        assertThrows(BadRequestException.class, () -> service.markRead(2L, aisyah));
        assertNull(someoneElses.getReadAt());
    }

    @Test
    @DisplayName("Mark all as read applies to the person's own notifications")
    void markAllRead() {
        service.markAllRead(aisyah);

        verify(userNotificationRepository).markAllRead(eq(aisyah), any());
    }

    private static UserNotification notification(Long id, UUID recipient) {
        UserNotification n = new UserNotification();
        n.setId(id);
        n.setRecipientId(recipient);
        n.setType("COURSE_ASSIGNED");
        n.setTitle("Title " + id);
        n.setBody("Body");
        n.setEmailStatus("SENT");
        return n;
    }
}
