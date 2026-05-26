package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Evaluation;
import com.tbm.careerpathlearning.model.EvaluationRatings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvaluationRatingsRepository extends JpaRepository<EvaluationRatings,Long> {
    List<EvaluationRatings> findByEvaluation(Evaluation evaluation);

    @Query("SELECT er FROM EvaluationRatings er " +
            "WHERE er.evaluation.staff.id = :staffId " +
            "AND er.createdAt = (" +
            "  SELECT MAX(er2.createdAt) " +
            "  FROM EvaluationRatings er2 " +
            "  WHERE er2.evaluation.staff.id = :staffId" +
            ")")
    List<EvaluationRatings> findLatestRatingsByStaffId(@Param("staffId") UUID staffId);


}
