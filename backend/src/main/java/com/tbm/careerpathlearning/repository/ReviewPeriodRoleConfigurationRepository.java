package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.ReviewPeriodRoleConfiguration;
import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewPeriodRoleConfigurationRepository extends JpaRepository<ReviewPeriodRoleConfiguration, Long> {
    void deleteAllByReviewPeriodId(Long reviewPeriodId);
    List<ReviewPeriodRoleConfiguration> findAllByReviewPeriodIdOrderByIdAsc(Long reviewPeriodId);
    Optional<ReviewPeriodRoleConfiguration> findByReviewPeriodIdAndRoleId(Long reviewPeriodId, Long roleId);

    @Query("""
            select configuration from ReviewPeriodRoleConfiguration configuration
            join fetch configuration.role
            join fetch configuration.reviewPeriod period
            where period.status in :statuses and period.endDate < :startDate
            order by period.endDate desc, period.startDate desc, period.id desc
            """)
    List<ReviewPeriodRoleConfiguration> findPublishedConfigurationsBefore(
            @Param("startDate") LocalDate startDate,
            @Param("statuses") Collection<AnnualKpiReviewPeriodStatus> statuses);
}
