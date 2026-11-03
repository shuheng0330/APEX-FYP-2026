package com.tbm.careerpathlearning.repository;
import com.tbm.careerpathlearning.model.EmployeeKpiAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EmployeeKpiAssignmentRepository extends JpaRepository<EmployeeKpiAssignment,Long> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="""
        INSERT INTO kpi_assessment_plan_version(assessment_id,review_period_id,level,version_id)
        SELECT DISTINCT a.id,a.review_period_id,:level,:versionId
        FROM kpi_assessment a JOIN employee_kpi_assignment x
          ON x.participant_id=a.participant_id AND x.review_period_id=a.review_period_id
        JOIN kpi k ON k.id=x.kpi_id AND k.version_id=:versionId
        WHERE a.status='DRAFT' AND a.review_period_id=:periodId
        ON CONFLICT(assessment_id,level) DO NOTHING
        """,nativeQuery=true)
    int bindMissingDraftVersions(Long periodId,String level,Long versionId);
    List<EmployeeKpiAssignment> findAllByParticipantId(Long participantId);
}
