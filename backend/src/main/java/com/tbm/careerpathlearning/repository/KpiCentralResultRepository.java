package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.KpiCentralResult;
import com.tbm.careerpathlearning.enums.KpiCentralResultStatus;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface KpiCentralResultRepository extends JpaRepository<KpiCentralResult,Long> {
    List<KpiCentralResult> findAllByReviewPeriodIdOrderByUpdatedAtDescIdDesc(Long periodId);
    List<KpiCentralResult> findAllByPlanVersionIdAndCheckpointIdOrderByEditionNumberDesc(Long versionId,Long checkpointId);
    boolean existsByPlanVersionIdAndCheckpointId(Long versionId,Long checkpointId);
    Optional<KpiCentralResult> findByPlanVersionIdAndCheckpointIdAndStatus(Long versionId,Long checkpointId,KpiCentralResultStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from KpiCentralResult r where r.id=:id")
    Optional<KpiCentralResult> lockById(Long id);
    // A preserved old version is offered only where a started assessment actually needs it.
    @Query(value="""
        SELECT count(DISTINCT p.id) FROM review_period_participant p
        JOIN review_checkpoint c ON c.review_period_id=p.review_period_id AND c.review_frequency=p.review_frequency
        JOIN kpi_plan_version v ON v.review_period_id=p.review_period_id
        JOIN kpi_plan plan ON plan.id=v.plan_id
        JOIN review_period_employee_level_configuration w ON w.id=p.employee_level_configuration_id
        WHERE v.id=:versionId AND c.id=:checkpointId
          AND (v.level='COMPANY' AND w.company_kpi_weight>0
            OR v.level='DEPARTMENT' AND w.department_kpi_weight>0 AND p.department_id=plan.department_id)
          AND (plan.current_version_id=v.id OR EXISTS (
            SELECT 1 FROM kpi_assessment a JOIN kpi_assessment_plan_version b ON b.assessment_id=a.id
            WHERE a.participant_id=p.id AND a.checkpoint_id=c.id AND b.version_id=v.id))
          AND EXISTS (SELECT 1 FROM employee_kpi_assignment x JOIN kpi k ON k.id=x.kpi_id
            WHERE x.participant_id=p.id AND k.version_id=v.id)
        """,nativeQuery=true)
    long eligibleParticipants(Long versionId,Long checkpointId);
}
