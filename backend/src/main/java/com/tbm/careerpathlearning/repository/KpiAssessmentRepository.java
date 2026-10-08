package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.KpiAssessment;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface KpiAssessmentRepository extends JpaRepository<KpiAssessment,Long> {
    Optional<KpiAssessment> findByParticipantIdAndCheckpointId(Long participantId,Long checkpointId);
    List<KpiAssessment> findAllByParticipantId(Long participantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from KpiAssessment a where a.id=:id")
    Optional<KpiAssessment> lockById(Long id);
}
