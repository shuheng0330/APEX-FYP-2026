package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AttitudeEvaluationFormat;
import lombok.Data;
import java.util.List;

@Data
public class AttitudeConfigurationOptionsDto {
    private List<AttitudeEvaluationFormat> formats;
    private List<AttitudeConfigurationDto.RoleMapping> roles;
}
