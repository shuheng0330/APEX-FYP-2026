package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.ReviewFrequency;
import com.tbm.careerpathlearning.model.ReviewCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewCheckpointRepository extends JpaRepository<ReviewCheckpoint, Long> {
    List<ReviewCheckpoint> findAllByReviewPeriodIdOrderByReviewFrequencyAscSequenceNumberAsc(Long reviewPeriodId);
    void deleteAllByReviewPeriodId(Long reviewPeriodId);
    List<ReviewCheckpoint> findAllByReviewPeriodIdAndReviewFrequencyOrderBySequenceNumberAsc(
            Long reviewPeriodId, ReviewFrequency reviewFrequency);
}
