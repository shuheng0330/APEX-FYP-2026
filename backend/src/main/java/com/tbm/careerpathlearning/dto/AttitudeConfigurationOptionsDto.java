package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AttitudeEvaluationFormat;
import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import lombok.Data;
import java.util.List;

@Data
public class AttitudeConfigurationOptionsDto {
    private List<AttitudeEvaluationFormat> formats;
    private List<AttitudeConfigurationDto.RoleMapping> roles;
    private List<ReviewPeriodOption> reviewPeriods;
    public record ReviewPeriodOption(Long id, String name, AnnualKpiReviewPeriodStatus status) {}
}
