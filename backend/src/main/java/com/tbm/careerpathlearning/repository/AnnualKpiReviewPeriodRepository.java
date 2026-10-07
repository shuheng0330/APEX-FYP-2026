package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.AnnualKpiReviewPeriodStatus;
import com.tbm.careerpathlearning.model.AnnualKpiReviewPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AnnualKpiReviewPeriodRepository extends JpaRepository<AnnualKpiReviewPeriod, Long> {
    List<AnnualKpiReviewPeriod> findAllByOrderByStartDateDescIdDesc();
    List<AnnualKpiReviewPeriod> findAllByStatus(AnnualKpiReviewPeriodStatus status);
    Optional<AnnualKpiReviewPeriod> findFirstByStatusInAndEndDateBeforeOrderByEndDateDescStartDateDescIdDesc(
            Collection<AnnualKpiReviewPeriodStatus> statuses, LocalDate startDate);
    boolean existsByName(String name);
    boolean existsByNameAndIdNot(String name, Long id);
    List<AnnualKpiReviewPeriod> findAllByStatusAndStartDateLessThanEqual(
            AnnualKpiReviewPeriodStatus status, LocalDate date);

    // A transaction-scoped PostgreSQL lock also protects publication when the table is empty.
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(20261006, 1)", nativeQuery = true)
    Integer lockConfiguration();

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
