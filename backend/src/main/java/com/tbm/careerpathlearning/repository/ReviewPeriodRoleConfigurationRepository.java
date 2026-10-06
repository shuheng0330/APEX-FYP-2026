package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.ReviewPeriodRoleConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReviewPeriodRoleConfigurationRepository extends JpaRepository<ReviewPeriodRoleConfiguration, Long> {
    List<ReviewPeriodRoleConfiguration> findAllByReviewPeriodIdOrderByIdAsc(Long reviewPeriodId);
    Optional<ReviewPeriodRoleConfiguration> findByReviewPeriodIdAndRoleId(Long reviewPeriodId, Long roleId);
}
