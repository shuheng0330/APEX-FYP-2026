package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.AttitudeAssessment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface AttitudeAssessmentRepository extends JpaRepository<AttitudeAssessment,Long> {
    Optional<AttitudeAssessment> findByParticipantId(Long participantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AttitudeAssessment a where a.id=:id")
    Optional<AttitudeAssessment> lockById(Long id);
}
