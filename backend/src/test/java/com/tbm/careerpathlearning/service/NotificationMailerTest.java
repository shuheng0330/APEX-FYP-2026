package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.UserNotification;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.UserNotificationRepository;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationMailerTest {

    @Mock private UserNotificationRepository userNotificationRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private JavaMailSender mailSender;

    @InjectMocks
    private NotificationMailer mailer;

    private final UUID aisyah = UUID.randomUUID();
    private UserNotification notification;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mailer, "clock", Clock.fixed(Instant.parse("2026-10-12T02:00:00Z"), ClockConfig.ZONE));
        ReflectionTestUtils.setField(mailer, "enabled", true);
        ReflectionTestUtils.setField(mailer, "redirectTo", "");
        ReflectionTestUtils.setField(mailer, "requireRedirect", false);
        ReflectionTestUtils.setField(mailer, "retryDelayMs", 0L);
        ReflectionTestUtils.setField(mailer, "from", "no-reply@apex.test");
        ReflectionTestUtils.setField(mailer, "appName", "APEX");
        ReflectionTestUtils.setField(mailer, "frontendOrigin", "http://localhost:4200");

        notification = new UserNotification();
        notification.setId(7L);
        notification.setRecipientId(aisyah);
        notification.setTitle("New training: Cash Handling");
        notification.setBody("Complete it by 26 Oct 2026.");
        notification.setLink("/my-learning/course/1");
        notification.setEmailStatus("PENDING");
        lenient().when(userNotificationRepository.findById(7L)).thenReturn(Optional.of(notification));
        Staff staff = new Staff();
        staff.setEmail("aisyah@example.com");
        lenient().when(staffRepository.findById(aisyah)).thenReturn(Optional.of(staff));
        lenient().when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    @DisplayName("The email goes to the recipient with the notification's title and a link into the app")
    void sends() throws Exception {
        mailer.deliver(7L);

        MimeMessage sent = sentMessage();
        assertEquals("aisyah@example.com", sent.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("New training: Cash Handling", sent.getSubject());
        assertTrue(sent.getContent().toString().contains("http://localhost:4200/my-learning/course/1"));
        assertEquals("SENT", notification.getEmailStatus());
        assertEquals(1, notification.getEmailAttempts());
        assertNotNull(notification.getEmailedAt());
    }

    @Test
    @DisplayName("A failed send is retried, and succeeding on the third attempt counts as sent")
    void retries() {
        doThrow(new MailSendException("timeout")).doThrow(new MailSendException("timeout")).doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        mailer.deliver(7L);

        verify(mailSender, times(3)).send(any(MimeMessage.class));
        assertEquals("SENT", notification.getEmailStatus());
        assertEquals(3, notification.getEmailAttempts());
        assertNull(notification.getEmailError());
    }

    @Test
    @DisplayName("After three failures the email is recorded as failed with the error, and nothing is thrown")
    void givesUpAfterThreeAttempts() {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> mailer.deliver(7L));

        verify(mailSender, times(3)).send(any(MimeMessage.class));
        assertEquals("FAILED", notification.getEmailStatus());
        assertEquals("SMTP down", notification.getEmailError());
    }

    @Test
    @DisplayName("With a test inbox set, every email goes there, naming the real recipient")
    void redirectsToTheTestInbox() throws Exception {
        ReflectionTestUtils.setField(mailer, "redirectTo", "tester@example.com");
        ReflectionTestUtils.setField(mailer, "requireRedirect", true);

        mailer.deliver(7L);

        MimeMessage sent = sentMessage();
        assertEquals("tester@example.com", sent.getRecipients(Message.RecipientType.TO)[0].toString());
        assertEquals("[Test, for aisyah@example.com] New training: Cash Handling", sent.getSubject());
    }

    @Test
    @DisplayName("In development without a test inbox, nothing is emailed")
    void devWithoutTestInboxSendsNothing() {
        ReflectionTestUtils.setField(mailer, "requireRedirect", true);

        mailer.deliver(7L);

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertEquals("SKIPPED", notification.getEmailStatus());
    }

    @Test
    @DisplayName("With email switched off, or no address on file, nothing is sent")
    void disabledOrNoAddress() {
        ReflectionTestUtils.setField(mailer, "enabled", false);
        mailer.deliver(7L);
        assertEquals("SKIPPED", notification.getEmailStatus());

        ReflectionTestUtils.setField(mailer, "enabled", true);
        when(staffRepository.findById(aisyah)).thenReturn(Optional.empty());
        mailer.deliver(7L);
        assertEquals("NO_ADDRESS", notification.getEmailStatus());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Text from the notification is escaped in the email")
    void escapesHtml() throws Exception {
        notification.setBody("<script>alert(1)</script>");

        mailer.deliver(7L);

        String html = sentMessage().getContent().toString();
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    private MimeMessage sentMessage() {
        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        return sent.getValue();
    }
}
