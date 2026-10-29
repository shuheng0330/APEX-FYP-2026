package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.model.TrainingProgram;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** Scheduled sessions up to a day whose attendance isn't submitted yet: the ones awaiting attendance (FR-14-10). */
    List<TrainingProgram> findAllByDeletedFalseAndStatusAndAttendanceSubmittedAtIsNullAndSessionDateLessThanEqual(
            TrainingSessionStatus status, LocalDate day);

    /**
     * The session, locked until the transaction ends. Every change to its seats goes through this, so two people
     * can't take the last seat at once.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TrainingProgram t where t.trainingId = :id and t.deleted = false")
    Optional<TrainingProgram> lockById(@Param("id") Long id);
}
