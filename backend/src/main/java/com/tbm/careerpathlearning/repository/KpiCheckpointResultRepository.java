package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.KpiCheckpointResult;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface KpiCheckpointResultRepository extends JpaRepository<KpiCheckpointResult,Long> {
    Optional<KpiCheckpointResult> findByParticipantIdAndCheckpointId(Long participantId,Long checkpointId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from KpiCheckpointResult r where r.participantId=:participantId and r.checkpointId=:checkpointId")
    Optional<KpiCheckpointResult> lockByScope(Long participantId,Long checkpointId);
}
