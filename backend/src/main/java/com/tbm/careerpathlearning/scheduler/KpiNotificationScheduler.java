package com.tbm.careerpathlearning.scheduler;

import com.tbm.careerpathlearning.service.KpiNotificationScheduleService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalTime;

@Component
public class KpiNotificationScheduler {
    private final KpiNotificationScheduleService service;
    private final Clock clock;
    public KpiNotificationScheduler(KpiNotificationScheduleService service,@Qualifier("annualKpiReviewClock") Clock clock) {
        this.service=service;this.clock=clock;
    }
    // Uses the same server calendar zone as annualKpiReviewClock. Keys make restart/retry harmless.
    @Scheduled(cron="0 */15 * * * *")
    @EventListener(ApplicationReadyEvent.class)
    public void scanIfDue() {
        if(LocalTime.now(clock).isBefore(LocalTime.of(9,0))) return;
        try { service.scanToday(); }
        catch(RuntimeException failure) {org.slf4j.LoggerFactory.getLogger(getClass()).error("KPI notification scan failed; it will retry on the next run",failure);}
    }
}
