package com.tbm.careerpathlearning.scheduler;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.model.TrainingJobRun;
import com.tbm.careerpathlearning.repository.TrainingJobRunRepository;
import com.tbm.careerpathlearning.service.SopReminderService;
import com.tbm.careerpathlearning.service.impl.SopNewJoiners;
import com.tbm.careerpathlearning.service.TrainerAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The daily 09:00 run (decision D4): at most once a day, catching up after the server was off. */
@ExtendWith(MockitoExtension.class)
class DailyTrainingJobsTest {

    @Mock private TrainingJobRunRepository trainingJobRunRepository;
    @Mock private TrainerAccessService trainerAccessService;
    @Mock private SopNewJoiners sopNewJoiners;
    @Mock private SopReminderService sopReminderService;

    @InjectMocks
    private DailyTrainingJobs jobs;

    @Test
    @DisplayName("Before 09:00 Malaysia time nothing runs, even when the server starts")
    void notBeforeNine() {
        at("2026-10-13T00:59:00Z"); // 08:59 in Malaysia

        assertFalse(jobs.runIfDue());

        verifyNoInteractions(trainerAccessService, sopNewJoiners, sopReminderService, trainingJobRunRepository);
    }

    @Test
    @DisplayName("From 09:00 the day's tasks run once and the run is recorded for that Malaysia date")
    void runsAndRecords() {
        at("2026-10-13T01:00:00Z"); // 09:00 in Malaysia
        when(trainerAccessService.lapseExpiredGrants()).thenReturn(2);
        when(sopNewJoiners.includeAll()).thenReturn(1);
        when(sopReminderService.sendReminders()).thenReturn(5);

        assertTrue(jobs.runIfDue());

        ArgumentCaptor<TrainingJobRun> run = ArgumentCaptor.forClass(TrainingJobRun.class);
        verify(trainingJobRunRepository).save(run.capture());
        assertEquals(LocalDate.of(2026, 10, 13), run.getValue().getRunDate());
        assertEquals("Trainer access grants lapsed: 2; new joiners assigned: 1; training reminders sent: 5",
                run.getValue().getOutcome());
    }

    @Test
    @DisplayName("A server started later the same day does not run the tasks again")
    void onceADay() {
        at("2026-10-13T06:00:00Z"); // 14:00 in Malaysia
        when(trainingJobRunRepository.existsByJobNameAndRunDate(DailyTrainingJobs.JOB, LocalDate.of(2026, 10, 13))).thenReturn(true);

        assertFalse(jobs.runIfDue());

        verifyNoInteractions(trainerAccessService);
    }

    @Test
    @DisplayName("A failed run is not recorded, so the next start tries again, and the error does not escape")
    void failedRunIsRetried() {
        at("2026-10-13T01:00:00Z");
        when(trainerAccessService.lapseExpiredGrants()).thenThrow(new IllegalStateException("database unavailable"));

        assertFalse(assertDoesNotThrow(() -> jobs.runIfDue()));

        verify(trainingJobRunRepository, never()).save(any());
    }

    @Test
    @DisplayName("Switched off, nothing runs")
    void disabled() {
        at("2026-10-13T01:00:00Z");
        ReflectionTestUtils.setField(jobs, "enabled", false);

        assertFalse(jobs.runIfDue());

        verifyNoInteractions(trainerAccessService, sopNewJoiners, sopReminderService, trainingJobRunRepository);
    }

    private void at(String instant) {
        ReflectionTestUtils.setField(jobs, "clock", Clock.fixed(Instant.parse(instant), ClockConfig.ZONE));
        ReflectionTestUtils.setField(jobs, "enabled", true);
    }
}
