package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.repository.UserNotificationRepository;
import com.tbm.careerpathlearning.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * A notification's email is handled only once the action that created it has been saved, and never for an
 * action that fails (NFR-016, P1-13). Runs against an in-memory database with real transactions. The email
 * step runs on its own thread (@EnableAsync is on the application class), so the test waits for its outcome.
 */
@DataJpaTest(properties = {
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "training.notifications.email-enabled=true",
        "training.notifications.retry-delay-ms=0",
        "mail-server.no-reply=no-reply@apex.test",
        "server.name=APEX",
        "frontend.origin=http://localhost:4200"
})
@Import({NotificationServiceImpl.class, NotificationMailer.class, ClockConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationAfterCommitTest {

    @Autowired private NotificationService notificationService;
    @Autowired private UserNotificationRepository userNotificationRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private JavaMailSender mailSender;

    @AfterEach
    void cleanUp() {
        userNotificationRepository.deleteAll();
    }

    @Test
    @DisplayName("The email step waits for the commit, then records its outcome")
    void emailAfterCommit() throws InterruptedException {
        UUID nobody = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        String beforeCommit = tx.execute(status -> {
            notificationService.notify(nobody, NotificationType.COURSE_ASSIGNED, "New training", "Due soon.", null);
            return userNotificationRepository.findAll().get(0).getEmailStatus();
        });

        assertEquals("PENDING", beforeCommit, "nothing is emailed while the action is still being saved");
        assertEquals("NO_ADDRESS", emailOutcome(),
                "after the commit the email step ran and saved its outcome (this recipient has no address)");
    }

    @Test
    @DisplayName("Nothing is kept or emailed for an action that fails")
    void nothingForAFailedAction() throws InterruptedException {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(status -> {
            notificationService.notify(UUID.randomUUID(), NotificationType.COURSE_ASSIGNED, "New training", "Due soon.", null);
            throw new IllegalStateException("the assignment failed");
        }));

        Thread.sleep(500); // give a wrongly started email step time to show up
        assertTrue(userNotificationRepository.findAll().isEmpty());
        verifyNoInteractions(mailSender);
    }

    /** The email status once the email step has finished, waiting up to 5 seconds. */
    private String emailOutcome() throws InterruptedException {
        String status = "PENDING";
        for (int i = 0; i < 100 && "PENDING".equals(status); i++) {
            Thread.sleep(50);
            status = userNotificationRepository.findAll().get(0).getEmailStatus();
        }
        return status;
    }
}
