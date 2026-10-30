package com.tbm.careerpathlearning.scheduler;

import com.tbm.careerpathlearning.model.TrainingJobRun;
import com.tbm.careerpathlearning.repository.TrainingJobRunRepository;
import com.tbm.careerpathlearning.service.TrainerAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * The training module's daily run at 09:00 Malaysia time (decision D4): work that is due because a date has
 * passed. It runs at most once a day, and if the server was off at 09:00 it runs when the server starts.
 * Each task is also safe to repeat, so even a second run never records or sends anything twice.
 */
@Slf4j
@Component
public class DailyTrainingJobs {

    static final String JOB = "DAILY_TRAINING";
    static final LocalTime RUN_AT = LocalTime.of(9, 0);

    @Autowired private TrainingJobRunRepository trainingJobRunRepository;
    @Autowired private TrainerAccessService trainerAccessService;
    @Autowired private Clock clock;

    @Value("${training.daily-jobs.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kuala_Lumpur")
    public void atNine() {
        runIfDue();
    }

    /** Catches up a run missed while the server was off. */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        runIfDue();
    }

    /**
     * Runs today's tasks, unless they already ran today or it is not yet 09:00. A failed run is not recorded,
     * so the next start of the server tries again.
     *
     * @return whether the tasks ran
     */
    public synchronized boolean runIfDue() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        if (!enabled || now.toLocalTime().isBefore(RUN_AT)
                || trainingJobRunRepository.existsByJobNameAndRunDate(JOB, now.toLocalDate())) {
            return false;
        }
        try {
            int lapsed = trainerAccessService.lapseExpiredGrants();

            TrainingJobRun run = new TrainingJobRun();
            run.setJobName(JOB);
            run.setRunDate(now.toLocalDate());
            run.setStartedAt(now.toOffsetDateTime());
            run.setFinishedAt(OffsetDateTime.now(clock));
            run.setOutcome("Trainer access grants lapsed: " + lapsed);
            trainingJobRunRepository.save(run);
            log.info("Daily training jobs for {}: {}", now.toLocalDate(), run.getOutcome());
            return true;
        } catch (RuntimeException e) {
            log.error("Daily training jobs for {} failed; they will run again when the server next starts", now.toLocalDate(), e);
            return false;
        }
    }
}
