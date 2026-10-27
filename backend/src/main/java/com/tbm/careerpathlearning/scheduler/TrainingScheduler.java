package com.tbm.careerpathlearning.scheduler;

import com.tbm.careerpathlearning.repository.TrainingInvitationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class TrainingScheduler {

    private static final Logger log = LoggerFactory.getLogger(TrainingScheduler.class);

    @Autowired
    private TrainingInvitationRepository invitationRepo;

    /** Malaysia time, whatever time zone the server runs in. */
    @Autowired
    private Clock clock;

    /**
     * Every minute: invitations to a mandatory session still unanswered when it starts become No Response
     * (FR-12-14). Safe to run more than once: only pending invitations change.
     */
    @Scheduled(cron = "0 * * * * *")
    public void markExpiredInvitations() {
        LocalDateTime now = LocalDateTime.now(clock);
        int count = invitationRepo.markUnansweredOnceStarted(now.toLocalDate(), now.toLocalTime(), now);
        if (count > 0) {
            log.info("Invitations unanswered when their session started, now No Response: {}", count);
        }
    }
}
