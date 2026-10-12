package com.tbm.careerpathlearning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * The training module's clock. "Today", due dates and overdue checks follow Malaysia time whatever
 * time zone the server runs in, so a course is not due a day early on a server set to UTC. Tests
 * replace it with a fixed clock.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
