package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import lombok.Data;
import java.util.List;

@Data
public class AttitudePeriodConfigurationDto {
    private Long reviewPeriodId;
    private String reviewPeriodName;
    private AnnualKpiReviewPeriodStatus reviewPeriodStatus;
    private AttitudeConfigurationDto configuration;
    private List<String> unmappedRoleNames;
    private boolean canBindInitially;
}
