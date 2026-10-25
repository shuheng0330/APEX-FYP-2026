package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.ReviewFrequency;
import com.tbm.careerpathlearning.model.ReviewCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewCheckpointRepository extends JpaRepository<ReviewCheckpoint, Long> {
    java.util.Optional<ReviewCheckpoint> findByReviewPeriodIdAndReviewFrequencyAndEndDate(
            Long reviewPeriodId, ReviewFrequency frequency, java.time.LocalDate endDate);
    List<ReviewCheckpoint> findAllByReviewPeriodIdOrderByReviewFrequencyAscSequenceNumberAsc(Long reviewPeriodId);
    void deleteAllByReviewPeriodId(Long reviewPeriodId);
    List<ReviewCheckpoint> findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(
            Long reviewPeriodId, ReviewFrequency reviewFrequency);
}
