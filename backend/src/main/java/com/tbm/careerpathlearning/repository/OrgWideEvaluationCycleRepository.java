package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.CycleStatus;
import com.tbm.careerpathlearning.model.EvaluationCycle;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrgWideEvaluationCycleRepository extends JpaRepository<EvaluationCycle, Long> {

    Optional<EvaluationCycle> findFirstByStatusOrderByEndDateDesc(CycleStatus status);

    Optional<EvaluationCycle> findFirstByStatusAndEndDateBeforeOrderByEndDateDesc(CycleStatus status, LocalDate endDate);

    @Query("""
            SELECT c
            FROM EvaluationCycle c
            WHERE c.status = :status
            ORDER BY c.endDate DESC
            """)
    List<EvaluationCycle> findClosedCycles(@Param("status") CycleStatus status, Pageable pageable);
}
