package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.AttitudeConfigurationStatus;
import com.tbm.careerpathlearning.model.AttitudeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AttitudeConfigurationRepository extends JpaRepository<AttitudeConfiguration,Long> {
    List<AttitudeConfiguration> findAllByOrderByCreatedAtDescIdDesc();
    Optional<AttitudeConfiguration> findFirstByStatusOrderByPublishedAtDescIdDesc(AttitudeConfigurationStatus status);
}
