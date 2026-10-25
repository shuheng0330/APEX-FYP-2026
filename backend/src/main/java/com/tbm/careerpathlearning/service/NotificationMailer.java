package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.NotificationEmailStatus;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.UserNotification;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.UserNotificationRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Emails each notification once the action that created it has been saved (NFR-016). It runs off the
 * request thread, tries up to three times, and records the outcome on the notification; a failed email
 * never delays or undoes the action, and the in-app notification is kept either way.
 *
 * <p>For testing, {@code training.notifications.redirect-to} sends every email to one inbox instead, with
 * the real recipient named in the subject. The dev profile sets {@code require-redirect}, so without a
 * test inbox nothing is emailed to real staff.
 */
@Slf4j
@Component
public class NotificationMailer {

    static final int MAX_ATTEMPTS = 3;

    @Autowired private UserNotificationRepository userNotificationRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private JavaMailSender mailSender;
    @Autowired private Clock clock;

    @Value("${training.notifications.email-enabled:true}")
    private boolean enabled;

    @Value("${training.notifications.redirect-to:}")
    private String redirectTo;

    @Value("${training.notifications.require-redirect:false}")
    private boolean requireRedirect;

    /** The wait before the second attempt; the third waits twice as long. */
    @Value("${training.notifications.retry-delay-ms:2000}")
    private long retryDelayMs;

    @Value("${mail-server.no-reply}")
    private String from;

    @Value("${server.name}")
    private String appName;

    @Value("${frontend.origin}")
    private String frontendOrigin;

    /**
     * Runs after the creating transaction commits. NOT_SUPPORTED keeps it out of that finished transaction,
     * so each status update below is saved in a transaction of its own.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void onCreated(NotificationCreatedEvent event) {
        deliver(event.notificationId());
    }

    /** Sends one notification's email and records the result on it. */
    public void deliver(Long notificationId) {
        UserNotification n = userNotificationRepository.findById(notificationId).orElse(null);
        if (n == null || NotificationEmailStatus.SKIPPED.name().equals(n.getEmailStatus())) {
            return;
        }
        String inbox = redirectTo == null ? "" : redirectTo.strip();
        if (!enabled || (requireRedirect && inbox.isEmpty())) {
            finish(n, NotificationEmailStatus.SKIPPED);
            return;
        }
        String address = staffRepository.findById(n.getRecipientId()).map(Staff::getEmail)
                .filter(e -> !e.isBlank()).orElse(null);
        if (address == null) {
            finish(n, NotificationEmailStatus.NO_ADDRESS);
            return;
        }
        String to = inbox.isEmpty() ? address : inbox;
        String subject = inbox.isEmpty() ? n.getTitle() : "[Test, for " + address + "] " + n.getTitle();

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            n.setEmailAttempts(attempt);
            try {
                send(to, subject, n);
                n.setEmailError(null);
                n.setEmailedAt(OffsetDateTime.now(clock));
                finish(n, NotificationEmailStatus.SENT);
                return;
            } catch (Exception e) {
                n.setEmailError(e.getMessage());
                log.warn("Notification {} email, attempt {} of {}, failed: {}", notificationId, attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS && !pause(retryDelayMs * attempt)) {
                    break;
                }
            }
        }
        finish(n, NotificationEmailStatus.FAILED);
        log.error("Notification {} email to {} failed after {} attempts", notificationId, to, n.getEmailAttempts());
    }

    private void send(String to, String subject, UserNotification n) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(new InternetAddress(from, appName));
        helper.setTo(to);
        helper.setSubject(subject);
        StringBuilder html = new StringBuilder()
                .append("<h3>").append(HtmlUtils.htmlEscape(n.getTitle())).append("</h3>")
                .append("<p>").append(HtmlUtils.htmlEscape(n.getBody())).append("</p>");
        if (n.getLink() != null) {
            String url = frontendOrigin + n.getLink();
            html.append("<p><a href='").append(HtmlUtils.htmlEscape(url)).append("' target='_blank'>Open in ")
                    .append(HtmlUtils.htmlEscape(appName)).append("</a></p>");
        }
        helper.setText(html.toString(), true);
        mailSender.send(message);
    }

    private void finish(UserNotification n, NotificationEmailStatus status) {
        n.setEmailStatus(status.name());
        userNotificationRepository.save(n);
    }

    /** Waits before the next attempt; false if the thread was interrupted, so the remaining attempts are skipped. */
    private static boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
