package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface AppraisalEvaluationRepository extends JpaRepository<Evaluation, Long> {

    @Query("""
            SELECT AVG(e.overallScore)
            FROM Evaluation e
            JOIN e.evaluationCycle c
            JOIN e.staff s
            WHERE s.id = :staffId
              AND e.overallScore IS NOT NULL
              AND c.endDate BETWEEN :startDate AND :endDate
            """)
    Double averageOverallScoreForReviewPeriod(
            @Param("staffId") UUID staffId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
