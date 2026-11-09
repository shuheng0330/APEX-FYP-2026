package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.model.EmployeeInsightRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

@MetadataOnlyLogging
public interface EmployeeInsightRequestRepository extends JpaRepository<EmployeeInsightRequest, UUID> {
    Optional<EmployeeInsightRequest> findByActorIdAndRequestKey(UUID actorId, UUID requestKey);
}
