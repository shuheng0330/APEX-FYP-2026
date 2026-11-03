package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.ReviewFrequency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ReviewPeriodDepartmentConfigurationDto {
    @NotNull @Positive private Long departmentId;
    private String departmentName;
    @NotNull private ReviewFrequency reviewFrequency;
}
