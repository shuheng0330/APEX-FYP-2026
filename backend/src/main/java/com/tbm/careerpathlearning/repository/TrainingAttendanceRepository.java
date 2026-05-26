package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrainingAttendanceRepository extends JpaRepository<TrainingAttendance, Long> {


    @Query("SELECT ta.staff FROM TrainingAttendance ta WHERE ta.trainingProgram.trainingId = :trainingId AND ta.trainingProgram.isDeleted = false")
    List<Staff> findStaffAttendanceByTrainingProgramId(@Param("trainingId") Long trainingId);

    @Query("SELECT DISTINCT ta.trainingProgram.trainingId FROM TrainingAttendance ta WHERE ta.staff.id = :staffId AND ta.trainingProgram.isDeleted = false")
    List<Long> findTrainingAttendanceIdByStaffId(@Param("staffId") UUID staffId);


    List<TrainingAttendance> findTrainingAttendanceByTrainingProgram_TrainingIdAndTrainingProgram_IsDeletedFalse(Long trainingId);
}
