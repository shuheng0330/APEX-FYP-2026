package com.tbm.careerpathlearning.scheduler;

import com.tbm.careerpathlearning.service.AnnualKpiReviewPeriodService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
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
