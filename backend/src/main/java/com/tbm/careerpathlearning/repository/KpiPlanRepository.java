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
    Optional<KpiPlan> findByReviewPeriodIdAndLevelAndDepartmentId(Long periodId, KpiLevel level, Long departmentId);
    List<KpiPlan> findAllByLevelAndDepartmentIdOrderByUpdatedAtDesc(KpiLevel level, Long departmentId);
    List<KpiPlan> findAllByLevelAndStatusOrderBySubmittedAtAscIdAsc(KpiLevel level, KpiPlanStatus status);
    Optional<KpiPlan> findByLevelAndOwnerParticipantId(KpiLevel level, Long ownerParticipantId);
    @Query("select p from KpiPlan p where p.level=:level and p.ownerParticipant.staff.id=:staffId order by p.updatedAt desc")
    List<KpiPlan> findAllByLevelAndOwnerStaffId(KpiLevel level, UUID staffId);
    List<KpiPlan> findAllByLevelAndStatusAndSubmittedToSuperiorIdOrderBySubmittedAtAscIdAsc(
            KpiLevel level, KpiPlanStatus status, UUID superiorId);
    @Query(value="select distinct p.* from kpi_plan p join kpi k on k.plan_id=p.id "
            + "join employee_kpi_assignment a on a.kpi_id=k.id "
            + "where a.participant_id=:participantId and a.review_period_id=:periodId order by p.level", nativeQuery=true)
    List<KpiPlan> findAssignedPlans(Long participantId, Long periodId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from KpiPlan p where p.id=:id")
    Optional<KpiPlan> lockById(Long id);
}
