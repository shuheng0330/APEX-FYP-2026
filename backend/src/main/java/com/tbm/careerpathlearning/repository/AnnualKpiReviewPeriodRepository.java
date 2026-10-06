package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AnnualKpiReviewPeriodRepository extends JpaRepository<AnnualKpiReviewPeriod, Long> {
    List<AnnualKpiReviewPeriod> findAllByOrderByStartDateDescIdDesc();
    List<AnnualKpiReviewPeriod> findAllByStatus(AnnualKpiReviewPeriodStatus status);
    boolean existsByName(String name);

    @Query("""
            select count(p) > 0 from AnnualKpiReviewPeriod p
            where p.status in :statuses and p.startDate <= :endDate and p.endDate >= :startDate
              and (:excludedId is null or p.id <> :excludedId)
            """)
    boolean existsOverlappingPeriod(@Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate,
                                    @Param("excludedId") Long excludedId,
                                    @Param("statuses") Collection<AnnualKpiReviewPeriodStatus> statuses);
}
