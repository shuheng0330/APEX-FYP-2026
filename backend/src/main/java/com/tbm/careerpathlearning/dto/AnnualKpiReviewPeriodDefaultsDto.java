package com.tbm.careerpathlearning.dto;

import lombok.Data;
import java.util.List;

@Data
public class AnnualKpiReviewPeriodDefaultsDto {
    private Long sourceReviewPeriodId;
    private String sourceReviewPeriodName;
    private List<ReviewPeriodEmployeeLevelConfigurationDto> employeeLevelConfigurations;
    private List<AnnualKpiReviewPeriodDto.RoleConfiguration> roleConfigurations;
}
