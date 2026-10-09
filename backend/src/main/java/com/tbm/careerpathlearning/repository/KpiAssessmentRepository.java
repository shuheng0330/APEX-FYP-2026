package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.KpiAssessment;
import com.tbm.careerpathlearning.enums.KpiAssessmentStatus;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface KpiAssessmentRepository extends JpaRepository<KpiAssessment,Long> {
    Optional<KpiAssessment> findByParticipantIdAndCheckpointId(Long participantId,Long checkpointId);
    List<KpiAssessment> findAllByParticipantId(Long participantId);
    @EntityGraph(attributePaths={"participant","participant.staff","participant.staff.manager","participant.reviewPeriod","checkpoint"})
    @Query("select a from KpiAssessment a where a.submittedToSuperiorId=:superiorId "
            + "and a.participant.staff.manager.id=:superiorId "
            + "and a.status in :statuses and (:reviewPeriodId is null or a.reviewPeriodId=:reviewPeriodId) "
            + "order by a.submittedAt asc, a.id asc")
    List<KpiAssessment> findReviewAssessments(UUID superiorId,Collection<KpiAssessmentStatus> statuses,Long reviewPeriodId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from KpiAssessment a where a.id=:id")
    Optional<KpiAssessment> lockById(Long id);
}
