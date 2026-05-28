package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AppraisalCategory;
import lombok.Data;

@Data
public class HrAppraisalActionDto {
    private AppraisalCategory hrOverrideCategory;
    private String hrOverrideReason;
    private String hrReturnReason;
}
