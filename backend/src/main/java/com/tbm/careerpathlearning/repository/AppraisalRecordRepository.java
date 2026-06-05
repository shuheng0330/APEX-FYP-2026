package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.AppraisalStatus;
import com.tbm.careerpathlearning.model.AppraisalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface AppraisalRecordRepository extends JpaRepository<AppraisalRecord, UUID> {

    Optional<AppraisalRecord> findByStaff_IdAndEvaluationCycle_Id(UUID staffId, Long evaluationCycleId);

    @Query("""
            SELECT ar
            FROM AppraisalRecord ar
            JOIN FETCH ar.staff s
            JOIN FETCH s.role r
            JOIN FETCH r.orgChart d
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
            JOIN FETCH s.role r
            JOIN FETCH r.orgChart d
            JOIN FETCH ar.manager m
            JOIN FETCH ar.evaluationCycle c
            LEFT JOIN FETCH ar.hrReviewer hr
            WHERE ar.status = :status
            ORDER BY ar.submittedAt DESC, ar.updatedAt DESC
            """)
    List<AppraisalRecord> findAllByStatusWithDetails(@Param("status") AppraisalStatus status);

    @Query("""
            SELECT ar
            FROM AppraisalRecord ar
            JOIN FETCH ar.staff s
            JOIN FETCH s.role r
            JOIN FETCH r.orgChart d
            JOIN FETCH ar.manager m
            JOIN FETCH ar.evaluationCycle c
            LEFT JOIN FETCH ar.hrReviewer hr
            WHERE c.id = :cycleId
              AND ar.status IN :statuses
            ORDER BY ar.submittedAt DESC, ar.updatedAt DESC
            """)
    List<AppraisalRecord> findAllByCycleIdAndStatusesWithDetails(
            @Param("cycleId") Long cycleId,
            @Param("statuses") Set<AppraisalStatus> statuses
    );

    @Query("""
            SELECT ar
            FROM AppraisalRecord ar
            JOIN FETCH ar.staff s
            JOIN FETCH s.role r
            JOIN FETCH r.orgChart d
            JOIN FETCH ar.manager m
            JOIN FETCH ar.evaluationCycle c
            LEFT JOIN FETCH ar.hrReviewer hr
            WHERE c.id = :cycleId
              AND s.id IN :staffIds
            ORDER BY s.name ASC
            """)
    List<AppraisalRecord> findAllByCycleIdAndStaffIdsWithDetails(
            @Param("cycleId") Long cycleId,
            @Param("staffIds") Set<UUID> staffIds
    );
}
