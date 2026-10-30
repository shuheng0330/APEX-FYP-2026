package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AppraisalCategory;
import lombok.Data;

@Data
public class HrAppraisalActionDto {
    private AppraisalCategory promotionHrOverrideCategory;
    private String promotionHrOverrideReason;
    private AppraisalCategory salaryHrOverrideCategory;
    private String salaryHrOverrideReason;
    private String hrReturnReason;
}
