package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.CompanyKpiResultDelegate;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface CompanyKpiResultDelegateRepository extends JpaRepository<CompanyKpiResultDelegate,Long> {
    boolean existsByReviewPeriodIdAndStaffIdAndRevokedAtIsNull(Long periodId,UUID staffId);
    List<CompanyKpiResultDelegate> findAllByReviewPeriodIdOrderByAssignedAtDescIdDesc(Long periodId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from CompanyKpiResultDelegate d where d.id=:id")
    Optional<CompanyKpiResultDelegate> lockById(Long id);
}
