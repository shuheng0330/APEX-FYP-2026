package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.AnnualAppraisalRecommendation;

public record AnnualAppraisalRequest(Long participantId, Long expectedLockVersion,
        AnnualAppraisalRecommendation recommendation, String justification) {}
