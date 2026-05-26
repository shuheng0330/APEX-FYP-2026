package com.tbm.careerpathlearning.scheduler;

import com.tbm.careerpathlearning.enums.Status;
import com.tbm.careerpathlearning.repository.TrainingInvitationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
public class TrainingScheduler {

    @Autowired
    private TrainingInvitationRepository invitationRepo;

    // Run at second 0 of every minute (Real-time accuracy)
    @Scheduled(cron = "0 * * * * *") // Every minute
    public void markExpiredInvitations() {
        LocalDateTime now = LocalDateTime.now();

        // Extract Date and Time separately
        LocalDate today = now.toLocalDate();
        LocalTime timeNow = now.toLocalTime();

        // Pass them to the repo
        int count = invitationRepo.bulkUpdateExpiredInvites(
                Status.NO_RESPONSE,
                Status.PENDING,
                today,
                timeNow
        );

        if (count > 0) {
            System.out.println("[System Audit] Auto-expired " + count + " invitations.");
        }
    }
}