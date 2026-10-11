package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.KpiPlanVersion;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface KpiPlanVersionRepository extends JpaRepository<KpiPlanVersion,Long> {
    List<KpiPlanVersion> findAllByPlanIdOrderByVersionNumberDesc(Long planId);
    @Query("select v from KpiPlanVersion v where v.versionNumber>1 order by v.updatedAt desc,v.id desc")
    List<KpiPlanVersion> revisions();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select v from KpiPlanVersion v where v.id=:id")
    Optional<KpiPlanVersion> lockById(Long id);
}
