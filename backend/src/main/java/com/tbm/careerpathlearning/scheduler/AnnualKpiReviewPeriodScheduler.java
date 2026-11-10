package com.tbm.careerpathlearning.scheduler;

import com.tbm.careerpathlearning.service.AnnualKpiReviewPeriodService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Opens due review periods at start-up and every minute. On by default; integration tests that run on H2 turn it off
 * with annual-kpi.review-period-scheduler.enabled=false, because the PostgreSQL advisory lock it takes does not exist there.
 */
@Component
@ConditionalOnProperty(name = "annual-kpi.review-period-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class AnnualKpiReviewPeriodScheduler {
    private final AnnualKpiReviewPeriodService service;

    public AnnualKpiReviewPeriodScheduler(AnnualKpiReviewPeriodService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 * * * * *")
    public void openDuePeriods() {
        service.openDuePeriods();
    }
}
