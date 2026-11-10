package com.tbm.careerpathlearning.scheduler;

import com.tbm.careerpathlearning.service.AnnualKpiReviewPeriodService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** The period-opening scheduler is on unless annual-kpi.review-period-scheduler.enabled is set to false. */
class AnnualKpiReviewPeriodSchedulerSwitchTest {

    private final AnnualKpiReviewPeriodService service = mock(AnnualKpiReviewPeriodService.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(AnnualKpiReviewPeriodService.class, () -> service)
            .withUserConfiguration(AnnualKpiReviewPeriodScheduler.class);

    @Test
    void schedulerIsOnWhenThePropertyIsMissing() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AnnualKpiReviewPeriodScheduler.class);
            context.getBean(AnnualKpiReviewPeriodScheduler.class).openDuePeriods();
            verify(service).openDuePeriods();
        });
    }

    @Test
    void schedulerIsOnWhenThePropertyIsTrue() {
        runner.withPropertyValues("annual-kpi.review-period-scheduler.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(AnnualKpiReviewPeriodScheduler.class));
    }

    @Test
    void schedulerIsOffWhenThePropertyIsFalse() {
        runner.withPropertyValues("annual-kpi.review-period-scheduler.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AnnualKpiReviewPeriodScheduler.class));
    }
}
