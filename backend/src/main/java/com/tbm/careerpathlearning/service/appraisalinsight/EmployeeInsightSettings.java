package com.tbm.careerpathlearning.service.appraisalinsight;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Technical UAT safeguards, not appraisal policy. Fail closed until explicitly configured. */
@Component @Getter
public class EmployeeInsightSettings {
    @Value("${appraisal.insight.employee.enabled:false}") private boolean enabled;
    @Value("${appraisal.insight.employee.database-name:}") private String databaseName;
    @Value("${appraisal.insight.employee.database-marker:}") private String databaseMarker;
    @Value("${appraisal.insight.employee.cooldown-seconds:60}") private int cooldownSeconds;
    @Value("${appraisal.insight.employee.daily-attempts:5}") private int dailyAttempts;
}
