package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AppraisalCategory;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AppraisalReadinessDto {
    private BigDecimal readinessScore;
    private AppraisalCategory systemCategory;
}
