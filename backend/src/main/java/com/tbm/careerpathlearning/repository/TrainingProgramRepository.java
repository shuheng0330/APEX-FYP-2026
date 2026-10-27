package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.model.TrainingProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingProgramRepository extends JpaRepository<TrainingProgram, Long> {

    /** The sessions between two dates, in the order they run (FR-12-11). */
    List<TrainingProgram> findAllByDeletedFalseAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(LocalDate from,
                                                                                                       LocalDate to);

    Optional<TrainingProgram> findByTrainingIdAndDeletedFalse(Long trainingId);
}
