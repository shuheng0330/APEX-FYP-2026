package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.*;
import com.tbm.careerpathlearning.repository.ReviewCheckpointRepository;
import org.springframework.stereotype.Service;

@Service
public class CompanyKpiCheckpointResolver {
    private final ReviewCheckpointRepository checkpoints;
    public CompanyKpiCheckpointResolver(ReviewCheckpointRepository checkpoints) { this.checkpoints = checkpoints; }

    public ReviewCheckpoint resolve(KpiAssessment assessment) {
        var period = assessment.getParticipant().getReviewPeriod();
        if (period.getFrequencyPolicy() != KpiFrequencyPolicy.DEPARTMENT_BASED) return assessment.getCheckpoint();
        return checkpoints.findByReviewPeriodIdAndReviewFrequencyAndEndDate(
                period.getId(), ReviewFrequency.MONTHLY, assessment.getCheckpoint().getEndDate())
                .orElseThrow(() -> new BadRequestException("The matching monthly Company reporting checkpoint is missing"));
    }
}
