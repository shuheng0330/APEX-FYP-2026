package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.CycleStatus;
import com.tbm.careerpathlearning.model.EvaluationCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EvaluationCycleRepository extends JpaRepository<EvaluationCycle, Long> {

    Optional<EvaluationCycle> findByStatus(CycleStatus status);

    Optional<EvaluationCycle> findFirstByStatus(CycleStatus status);

    Optional<EvaluationCycle> findFirstByStatusOrderByStartDateAsc(CycleStatus status);

    List<EvaluationCycle> findAllByOrderByStartDateDesc();

    // Auto-close
    List<EvaluationCycle> findAllByStatusAndEndDateBefore(CycleStatus status, LocalDate date);

    // Auto-open
    List<EvaluationCycle> findAllByStatusAndStartDateLessThanEqual(CycleStatus status, LocalDate date);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM EvaluationCycle c WHERE c.status = 'OPEN'")
    Optional<EvaluationCycle> findActiveCycle();
}
