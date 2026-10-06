package com.tbm.careerpathlearning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class AnnualKpiReviewClockConfig {
    @Bean("annualKpiReviewClock")
    public Clock annualKpiReviewClock() {
        return Clock.systemDefaultZone();
    }
}
