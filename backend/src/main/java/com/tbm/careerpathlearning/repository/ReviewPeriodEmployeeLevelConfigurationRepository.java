package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.ReviewPeriodEmployeeLevelConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReviewPeriodEmployeeLevelConfigurationRepository extends JpaRepository<ReviewPeriodEmployeeLevelConfiguration, Long> {
    List<ReviewPeriodEmployeeLevelConfiguration> findAllByReviewPeriodIdOrderByEmployeeLevelDisplayOrderAsc(Long periodId);
    void deleteAllByReviewPeriodId(Long periodId);
}
