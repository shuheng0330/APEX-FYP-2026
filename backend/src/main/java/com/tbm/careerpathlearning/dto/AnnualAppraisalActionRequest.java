package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AnnualAppraisalRecommendation;

public record AnnualAppraisalActionRequest(Long expectedLockVersion,
        AnnualAppraisalRecommendation finalDecision, String reason) {}
