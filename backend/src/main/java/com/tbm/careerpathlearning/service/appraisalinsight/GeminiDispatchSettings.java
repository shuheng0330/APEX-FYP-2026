package com.tbm.careerpathlearning.service.appraisalinsight;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Operator-supplied project budgets; no free-tier quota is assumed. */
@Component @Getter
public class GeminiDispatchSettings {
    @Value("${gemini.coordination.enabled:false}") private boolean enabled;
    @Value("${gemini.coordination.project-key:}") private String projectKey;
    @Value("${gemini.coordination.config-revision:1}") private long configRevision=1;
    @Value("${gemini.model:gemini-3.1-flash-lite}") private String model;
    @Value("${appraisal.insight.workers:2}") private int workers;
    @Value("${appraisal.insight.queue-capacity:8}") private int queueCapacity;
    @Value("${appraisal.insight.queue-wait-seconds:600}") private int queueWaitSeconds;
    @Value("${gemini.coordination.requests-per-minute:0}") private int requestsPerMinute;
    @Value("${gemini.coordination.input-tokens-per-minute:0}") private long tokensPerMinute;
    @Value("${gemini.coordination.requests-per-day:0}") private int requestsPerDay;

    public boolean configured(String model) {
        return enabled && configRevision>0 && projectKey!=null && projectKey.matches("[A-Za-z0-9._-]{1,80}")
                && model!=null && model.matches("[A-Za-z0-9._-]{1,100}")
                && workers>=1 && workers<=5 && queueCapacity>=1 && queueCapacity<=128
                && queueWaitSeconds>=1 && queueWaitSeconds<=3600
                && requestsPerMinute>0 && tokensPerMinute>0 && requestsPerDay>0;
    }
}
