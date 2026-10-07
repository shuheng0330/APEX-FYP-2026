package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.KpiPlan;
import com.tbm.careerpathlearning.enums.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface KpiPlanRepository extends JpaRepository<KpiPlan,Long> {
    boolean existsByReviewPeriodId(Long periodId);
    Optional<KpiPlan> findByReviewPeriodIdAndLevel(Long periodId, KpiLevel level);
    List<KpiPlan> findAllByLevelOrderByUpdatedAtDesc(KpiLevel level);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from KpiPlan p where p.id=:id")
    Optional<KpiPlan> lockById(Long id);
}
