package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrgWideEvaluationRepository extends JpaRepository<Evaluation, Long> {

    interface DepartmentAverageProjection {
        String getDepartmentName();
        Double getAverageScore();
        Long getStaffCount();
    }

    interface StaffLatestScoreProjection {
        String getStaffName();
        Double getScore();
    }

    interface CompetencyAverageProjection {
        String getDepartmentName();
        String getCompetencyName();
        Double getAverageRating();
    }

    interface DepartmentTrendProjection {
        Integer getCycleYear();
        String getDepartmentName();
        Double getAverageScore();
    }

    @Query("""
            SELECT COUNT(DISTINCT s.id)
            FROM Evaluation e
            JOIN e.staff s
            WHERE s.isDeleted = false
            """)
    Long countDistinctEvaluatedStaff();

    @Query("""
            SELECT AVG(e.overallScore)
            FROM Evaluation e
            JOIN e.staff s
            WHERE e.evaluationCycle.id = :cycleId
              AND s.isDeleted = false
            """)
    Double findAverageScoreByCycle(@Param("cycleId") Long cycleId);

    @Query("""
            SELECT d.name AS departmentName,
                   AVG(e.overallScore) AS averageScore,
                   COUNT(DISTINCT s.id) AS staffCount
            FROM Evaluation e
            JOIN e.staff s
            JOIN s.role r
            JOIN r.orgChart d
            WHERE e.evaluationCycle.id = :cycleId
              AND s.isDeleted = false
            GROUP BY d.name
            ORDER BY AVG(e.overallScore) DESC
            """)
    List<DepartmentAverageProjection> findDepartmentAveragesByCycle(@Param("cycleId") Long cycleId);

    @Query("""
            SELECT s.name AS staffName,
                   e.overallScore AS score
            FROM Evaluation e
            JOIN e.staff s
            JOIN e.evaluationCycle cycle
            WHERE s.isDeleted = false
              AND cycle.endDate = (
                  SELECT MAX(cycle2.endDate)
                  FROM Evaluation e2
                  JOIN e2.evaluationCycle cycle2
                  WHERE e2.staff.id = s.id
              )
            ORDER BY s.name
            """)
    List<StaffLatestScoreProjection> findLatestScoresByStaff();

    @Query("""
            SELECT d.name AS departmentName,
                   c.name AS competencyName,
                   AVG(rating.rating) AS averageRating
            FROM Evaluation e
            JOIN e.staff s
            JOIN s.role role
            JOIN role.orgChart d
            JOIN e.ratings rating
            JOIN rating.competency c
            WHERE e.evaluationCycle.id = :cycleId
              AND s.isDeleted = false
            GROUP BY d.name, c.name
            ORDER BY d.name ASC, c.name ASC
            """)
    List<CompetencyAverageProjection> findCompetencyAveragesByCycle(@Param("cycleId") Long cycleId);

    @Query("""
            SELECT d.name AS departmentName,
                   c.name AS competencyName,
                   AVG(rating.rating) AS averageRating
            FROM Evaluation e
            JOIN e.staff s
            JOIN s.role role
            JOIN role.orgChart d
            JOIN e.ratings rating
            JOIN rating.competency c
            WHERE e.evaluationCycle.id = :cycleId
              AND s.isDeleted = false
              AND LOWER(d.name) = LOWER(:departmentName)
            GROUP BY d.name, c.name
            ORDER BY d.name ASC, c.name ASC
            """)
    List<CompetencyAverageProjection> findCompetencyAveragesByCycleAndDepartment(
            @Param("cycleId") Long cycleId,
            @Param("departmentName") String departmentName
    );

    @Query("""
            SELECT YEAR(cycle.endDate) AS cycleYear,
                   d.name AS departmentName,
                   AVG(e.overallScore) AS averageScore
            FROM Evaluation e
            JOIN e.evaluationCycle cycle
            JOIN e.staff s
            JOIN s.role role
            JOIN role.orgChart d
            WHERE cycle.id IN :cycleIds
              AND s.isDeleted = false
            GROUP BY YEAR(cycle.endDate), d.name
            ORDER BY YEAR(cycle.endDate) ASC, d.name ASC
            """)
    List<DepartmentTrendProjection> findDepartmentTrendByCycleIds(@Param("cycleIds") List<Long> cycleIds);
}
