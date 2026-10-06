package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.enums.ReviewFrequency;
import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import com.tbm.careerpathlearning.model.ReviewCheckpoint;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class ReviewCheckpointGenerator {
    public List<ReviewCheckpoint> generate(AnnualKpiReviewPeriod period, ReviewFrequency frequency) {
        Objects.requireNonNull(period, "Review period is required");
        Objects.requireNonNull(frequency, "Review frequency is required");
        LocalDate start = period.getStartDate();
        LocalDate end = period.getEndDate();
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        Integer selfDays = period.getSelfAssessmentDaysAfterCheckpoint();
        Integer superiorDays = period.getSuperiorAssessmentDaysAfterSelfDeadline();
        if (selfDays == null || superiorDays == null || selfDays <= 0 || superiorDays <= 0) {
            throw new IllegalArgumentException("Assessment deadline offsets must be positive");
        }
        List<ReviewCheckpoint> checkpoints = new ArrayList<>();
        LocalDate checkpointStart = start;
        while (!checkpointStart.isAfter(end)) {
            LocalDate boundary = switch (frequency) {
                case MONTHLY -> YearMonth.from(checkpointStart).atEndOfMonth();
                case QUARTERLY -> YearMonth.of(checkpointStart.getYear(),
                        ((checkpointStart.getMonthValue() - 1) / 3 + 1) * 3).atEndOfMonth();
                case ANNUALLY -> end;
            };
            LocalDate checkpointEnd = boundary.isAfter(end) ? end : boundary;
            ReviewCheckpoint checkpoint = new ReviewCheckpoint();
            checkpoint.setReviewPeriod(period);
            checkpoint.setReviewFrequency(frequency);
            checkpoint.setSequenceNumber(checkpoints.size() + 1);
            checkpoint.setStartDate(checkpointStart);
            checkpoint.setEndDate(checkpointEnd);
            checkpoint.setSelfAssessmentDeadline(checkpointEnd.plusDays(selfDays));
            checkpoint.setSuperiorAssessmentDeadline(checkpoint.getSelfAssessmentDeadline().plusDays(superiorDays));
            checkpoints.add(checkpoint);
            if (checkpointEnd.equals(end)) break;
            checkpointStart = checkpointEnd.plusDays(1);
        }
        return checkpoints;
    }
}
