package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.ReviewPeriodParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewPeriodParticipantRepository extends JpaRepository<ReviewPeriodParticipant, Long> {
    boolean existsByReviewPeriodId(Long reviewPeriodId);
    List<ReviewPeriodParticipant> findAllByReviewPeriodId(Long reviewPeriodId);
    Optional<ReviewPeriodParticipant> findByReviewPeriodIdAndStaffId(Long reviewPeriodId, UUID staffId);
    List<ReviewPeriodParticipant> findAllByReviewPeriodIdAndSuperiorId(Long reviewPeriodId, UUID superiorId);
    List<ReviewPeriodParticipant> findAllByStaffIdOrderByReviewPeriodStartDateDesc(UUID staffId);
}
