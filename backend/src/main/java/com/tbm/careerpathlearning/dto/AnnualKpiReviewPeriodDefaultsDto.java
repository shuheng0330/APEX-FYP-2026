package com.tbm.careerpathlearning.dto;

import lombok.Data;
import java.util.List;

@Data
public class AnnualKpiReviewPeriodDefaultsDto {
    private Long sourceReviewPeriodId;
    private List<ReviewPeriodEmployeeLevelConfigurationDto> employeeLevelConfigurations;
    private List<AnnualKpiReviewPeriodDto.RoleConfiguration> roleConfigurations;
}
