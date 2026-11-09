package com.tbm.careerpathlearning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.tbm.careerpathlearning.service.appraisalinsight.*;

@Configuration
public class AppraisalInsightConfig {
    @Bean(name = "appraisalInsightExecutor")
    public SharedAppraisalExecutor appraisalInsightExecutor(GeminiDispatchGate gate,GeminiDispatchSettings settings) {
        if (settings.getWorkers()<1 || settings.getWorkers()>5 || settings.getQueueCapacity()<1 || settings.getQueueCapacity()>128)
            throw new IllegalArgumentException("Appraisal worker and queue limits are invalid");
        var executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("appraisal-insight-");
        executor.setCorePoolSize(settings.getWorkers());
        executor.setMaxPoolSize(settings.getWorkers());
        executor.setQueueCapacity(settings.getQueueCapacity());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return new SharedAppraisalExecutor(gate,executor);
    }
}
