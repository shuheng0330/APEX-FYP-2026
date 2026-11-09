package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.model.EmployeeInsightScope;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

@MetadataOnlyLogging
public interface EmployeeInsightScopeRepository extends JpaRepository<EmployeeInsightScope, Long> {
    @Query(value="select exists(select 1 from appraisal_employee_insight_scope s where s.review_period_id=:periodId "
            + "or exists(select 1 from jsonb_array_elements_text(case when jsonb_typeof(s.source_roots->'historicalParticipantIds')='array' "
            + "then s.source_roots->'historicalParticipantIds' else '[]'::jsonb end) h(id) "
            + "join review_period_participant p on p.id::text=h.id where p.review_period_id=:periodId))",nativeQuery=true)
    boolean referencesReviewPeriod(@Param("periodId") Long periodId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from EmployeeInsightScope s where s.participantId=:participantId")
    Optional<EmployeeInsightScope> lockByParticipantId(@Param("participantId") Long participantId);
}
