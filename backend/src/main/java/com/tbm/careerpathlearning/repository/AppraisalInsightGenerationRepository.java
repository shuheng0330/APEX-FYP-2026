package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.model.AppraisalInsightGeneration;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@MetadataOnlyLogging
public interface AppraisalInsightGenerationRepository extends JpaRepository<AppraisalInsightGeneration, UUID> {
    Optional<AppraisalInsightGeneration> findByOwnerIdAndRequestKey(UUID ownerId, UUID requestKey);

    @Query("select count(s) from Staff s join s.role r where s.id=:owner and s.isDeleted=false "
            + "and s.accountStatus=com.tbm.careerpathlearning.enums.StaffAccountStatus.ACTIVE and r.isDeleted=false "
            + "and exists (select ra.id from RoleAuthority ra where ra.role=r and ra.authority.name in :permissions)")
    long countAuthorisedPreviewAccounts(@Param("owner") UUID owner,
            @Param("permissions") Set<com.tbm.careerpathlearning.enums.AuthorityName> permissions);

    @Modifying
    @Query("update AppraisalInsightGeneration g set g.status='FAILED', g.errorCode='INTERRUPTED', "
            + "g.activeKey=null, g.completedAt=:now, g.lockVersion=g.lockVersion+1 "
            + "where g.status in ('QUEUED','RUNNING') and g.createdAt < :beforeStartup")
    int interruptUnfinished(@Param("beforeStartup") OffsetDateTime beforeStartup, @Param("now") OffsetDateTime now);
}
