package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.AttitudeAssessment;
import com.tbm.careerpathlearning.enums.AttitudeAssessmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface AttitudeAssessmentRepository extends JpaRepository<AttitudeAssessment,Long> {
    Optional<AttitudeAssessment> findByParticipantId(Long participantId);
    @EntityGraph(attributePaths={"participant","participant.staff","participant.staff.manager","participant.reviewPeriod"})
    @Query("select a from AttitudeAssessment a where a.submittedToSuperiorId=:superiorId "
            + "and a.participant.staff.manager.id=:superiorId "
            + "and a.status in :statuses and (:reviewPeriodId is null or a.reviewPeriodId=:reviewPeriodId) "
            + "order by a.submittedAt asc, a.id asc")
    List<AttitudeAssessment> findReviewAssessments(UUID superiorId,Collection<AttitudeAssessmentStatus> statuses,Long reviewPeriodId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AttitudeAssessment a where a.id=:id")
    Optional<AttitudeAssessment> lockById(Long id);
}
