package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.model.TrainingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrainingRegistrationRepository extends JpaRepository<TrainingRegistration, Long> {

    @Query("SELECT tr.staff FROM TrainingRegistration tr WHERE tr.training.trainingId = :trainingId AND tr.training.isDeleted =  false")
    List<Staff> getRegisteredStaffByTrainingId(@Param("trainingId") Long trainingId);

    @Query("SELECT tr.training.trainingId, COUNT(tr) FROM TrainingRegistration tr WHERE tr.training.trainingId  IN :trainingIds AND tr.training.isDeleted = false GROUP BY tr.training.trainingId")
    List<Object[]> countRegistrationsByTrainingIds(@Param("trainingIds") List<Long> trainingIds);

    @Query("""
        SELECT COUNT(tr)
        FROM TrainingRegistration tr
        WHERE tr.training.trainingId = :trainingId
          AND tr.training.isDeleted = false
    """)
    long countByTraining_TrainingId(Long trainingId);

    @Query("""
        SELECT tr.training
        FROM TrainingRegistration tr
        WHERE tr.staff.id = :staffId
          AND tr.training.isDeleted = false
    """)
    List<TrainingProgram> findActiveTrainingsByStaffId(@Param("staffId") UUID staffId);

    @Query("""
        SELECT new com.tbm.careerpathlearning.dto.StaffConflictDTO(
            tr.staff.id,
            tr.training.title,
            tr.training.startTime,
            tr.training.endTime,
            tr.training.venue
        )
        FROM TrainingRegistration tr
        WHERE tr.training.isDeleted = false
          AND tr.training.trainingId <> :currentTrainingId
          AND (
               (tr.training.startDate <= :endDate AND tr.training.endDate >= :startDate)
          )
          AND (
               (tr.training.startTime <= :endTime AND tr.training.endTime >= :startTime)
          )
    """)
    List<com.tbm.careerpathlearning.dto.StaffConflictDTO> findConflictingRegistrations(
            @Param("currentTrainingId") Long currentTrainingId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime
    );

    @Query("""
        SELECT COUNT(tr) > 0
        FROM TrainingRegistration tr
        WHERE tr.staff.id = :staffId
          AND tr.training.isDeleted = false
          AND tr.training.trainingId <> :currentTrainingId
          AND (
               (tr.training.startDate <= :endDate AND tr.training.endDate >= :startDate)
          )
          AND (
               (tr.training.startTime <= :endTime AND tr.training.endTime >= :startTime)
          )
    """)
    boolean hasConflict(
            @Param("staffId") UUID staffId,
            @Param("currentTrainingId") Long currentTrainingId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime
    );

}
