package com.tbm.careerpathlearning.repository;
import com.tbm.careerpathlearning.model.TrainingProgram;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TrainingProgramRepository extends JpaRepository<TrainingProgram, Long> {

    List<TrainingProgram> findByIsDeletedFalse();

    Page<TrainingProgram> findByIsDeletedFalse(Pageable pageable);

    Optional<TrainingProgram> findByTrainingIdAndIsDeletedFalse(Long trainingId);

    List<TrainingProgram> findByTrainingIdInAndIsDeletedFalse(List<Long> ids);


    @Query("""
        SELECT tr.training
        FROM TrainingRegistration tr
        WHERE tr.staff.id = :staffId
          AND tr.training.isDeleted = false
    """)
    List<TrainingProgram> findActiveTrainingsByStaffId(@Param("staffId") UUID staffId);


}

