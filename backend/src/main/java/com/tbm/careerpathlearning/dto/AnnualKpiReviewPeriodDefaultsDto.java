package com.tbm.careerpathlearning.dto;

import lombok.Data;
import java.util.List;

@Data
public class AnnualKpiReviewPeriodDefaultsDto {
    private Long sourceReviewPeriodId;
    private String sourceReviewPeriodName;
    private List<ReviewPeriodEmployeeLevelConfigurationDto> employeeLevelConfigurations;
    private List<AnnualKpiReviewPeriodDto.RoleConfiguration> roleConfigurations;
    private List<ReviewPeriodDepartmentConfigurationDto> departmentConfigurations;
    private com.tbm.careerpathlearning.enums.KpiFrequencyPolicy frequencyPolicy;
    private com.tbm.careerpathlearning.enums.AnnualKpiConsolidationMethod annualKpiConsolidationMethod;
}
