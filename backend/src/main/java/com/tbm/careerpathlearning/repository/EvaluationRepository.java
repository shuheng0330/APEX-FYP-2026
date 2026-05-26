package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Evaluation;
import com.tbm.careerpathlearning.model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {

    List<Evaluation> findByStaff_Id(UUID staffId);

    @Query("SELECT e FROM Evaluation e WHERE e.staff = :staff ORDER BY e.createdAt DESC")
    Evaluation findLatestEvaluation(@Param("staff") Staff staff);

    List<Evaluation> findAllByStaffIdIn(Set<UUID> staffIds);

    @Query("""
    SELECT DISTINCT e
    FROM Evaluation e
    LEFT JOIN FETCH e.staff
    LEFT JOIN FETCH e.ratings
""")
    List<Evaluation> findAllWithRatings();

    @Query("""
    SELECT DISTINCT e
    FROM Evaluation e
    JOIN FETCH e.staff s
    LEFT JOIN FETCH e.ratings r
    WHERE s.id = :staffId
""")
    List<Evaluation> findAllByStaffWithRatings(@Param("staffId") UUID staffId);

    @Query("""
    SELECT DISTINCT e
    FROM Evaluation e
    JOIN FETCH e.staff s
    LEFT JOIN FETCH e.ratings r
    WHERE s.manager.id = :managerId
      AND s.isDeleted = false
""")
    List<Evaluation> findAllDownlineEvaluations(@Param("managerId") UUID managerId);

}
