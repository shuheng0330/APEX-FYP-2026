package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.model.AttitudeConfigurationAssignmentActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

@MetadataOnlyLogging
public interface AttitudeConfigurationAssignmentActivityRepository extends JpaRepository<AttitudeConfigurationAssignmentActivity,Long> {
    Optional<AttitudeConfigurationAssignmentActivity> findByRequestId(UUID requestId);
    List<AttitudeConfigurationAssignmentActivity> findAllByReviewPeriodIdOrderByIdAsc(Long reviewPeriodId);
}
