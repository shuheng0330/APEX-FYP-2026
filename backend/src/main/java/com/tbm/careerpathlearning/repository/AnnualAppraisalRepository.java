package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.AnnualAppraisal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface AnnualAppraisalRepository extends JpaRepository<AnnualAppraisal,Long> {
    Optional<AnnualAppraisal> findByParticipantId(Long participantId);
    List<AnnualAppraisal> findAllByReviewPeriodIdOrderByUpdatedAtDescIdDesc(Long reviewPeriodId);
    List<AnnualAppraisal> findAllByEmployeeIdOrderBySubmittedAtDescIdDesc(UUID employeeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AnnualAppraisal a where a.id=:id")
    Optional<AnnualAppraisal> lockById(Long id);
}
