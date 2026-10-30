package com.tbm.careerpathlearning.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ReviewPeriodEmployeeLevelConfigurationDto {
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;
    @NotNull @Positive
    private Long employeeLevelId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String employeeLevelCode;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String employeeLevelName;
    private BigDecimal companyKpiWeight;
    private BigDecimal departmentKpiWeight;
    private BigDecimal individualKpiWeight;
}
