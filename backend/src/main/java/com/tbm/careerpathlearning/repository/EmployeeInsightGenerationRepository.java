package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.model.EmployeeInsightGeneration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@MetadataOnlyLogging
public interface EmployeeInsightGenerationRepository extends JpaRepository<EmployeeInsightGeneration, UUID> {
    List<EmployeeInsightGeneration> findAllByParticipantIdOrderByCreatedAtDescIdDesc(Long participantId);

    @Query("select g from EmployeeInsightGeneration g where g.activeContext=:participantId")
    Optional<EmployeeInsightGeneration> findActive(@Param("participantId") Long participantId);

    @Query("select max(g.insightVersion) from EmployeeInsightGeneration g where g.participantId=:participantId")
    Optional<Integer> maxVersion(@Param("participantId") Long participantId);

    @Query("select max(g.retryAt) from EmployeeInsightGeneration g")
    Optional<OffsetDateTime> maxProviderRetryAt();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from EmployeeInsightGeneration g where g.id=:id")
    Optional<EmployeeInsightGeneration> lockById(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmployeeInsightGeneration g set g.status='FAILED', g.errorCode='INTERRUPTED', "
            + "g.activeContext=null, g.leaseUntil=null, g.completedAt=:now, g.lockVersion=g.lockVersion+1 "
            + "where g.status in ('QUEUED','RUNNING') and g.leaseUntil <= :now")
    int interruptExpired(@Param("now") OffsetDateTime now);
}
