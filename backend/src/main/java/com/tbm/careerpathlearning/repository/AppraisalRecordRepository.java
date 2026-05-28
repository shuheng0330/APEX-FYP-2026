package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.AppraisalStatus;
import com.tbm.careerpathlearning.model.AppraisalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppraisalRecordRepository extends JpaRepository<AppraisalRecord, UUID> {

    Optional<AppraisalRecord> findByStaff_IdAndEvaluationCycle_Id(UUID staffId, Long evaluationCycleId);

    @Query("""
            SELECT ar
            FROM AppraisalRecord ar
            JOIN FETCH ar.staff s
            JOIN FETCH ar.manager m
            JOIN FETCH ar.evaluationCycle c
            LEFT JOIN FETCH ar.hrReviewer hr
            WHERE s.id = :staffId
            ORDER BY ar.createdAt DESC
            """)
    List<AppraisalRecord> findAllByStaffIdOrderByCreatedAtDesc(@Param("staffId") UUID staffId);

    @Query("""
            SELECT ar
            FROM AppraisalRecord ar
            JOIN FETCH ar.staff s
            JOIN FETCH ar.manager m
            JOIN FETCH ar.evaluationCycle c
            LEFT JOIN FETCH ar.hrReviewer hr
            WHERE ar.status = :status
            ORDER BY ar.submittedAt DESC, ar.updatedAt DESC
            """)
    List<AppraisalRecord> findAllByStatusWithDetails(@Param("status") AppraisalStatus status);
}
