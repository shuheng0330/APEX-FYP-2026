package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.TrainingInvitationStatus;
import com.tbm.careerpathlearning.model.TrainingInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface TrainingInvitationRepository extends JpaRepository<TrainingInvitation, Long> {

    List<TrainingInvitation> findAllByTrainingIdAndStatus(Long trainingId, TrainingInvitationStatus status);

    boolean existsByTrainingId(Long trainingId);

    /** Which of these sessions anyone has been invited to. */
    @Query("select distinct i.trainingId from TrainingInvitation i where i.trainingId in :trainingIds")
    List<Long> findInvitedTrainingIds(@Param("trainingIds") Collection<Long> trainingIds);

    /**
     * Marks the invitations still unanswered once their session has started as No Response, answered {@code now}.
     * {@code today} and {@code time} are now on the Malaysia clock. Safe to run again: only pending invitations change.
     */
    @Modifying
    @Transactional
    @Query("""
            update TrainingInvitation i
            set i.status = com.tbm.careerpathlearning.enums.TrainingInvitationStatus.NO_RESPONSE, i.respondedAt = :now
            where i.status = com.tbm.careerpathlearning.enums.TrainingInvitationStatus.PENDING
              and i.trainingId in (select t.trainingId from TrainingProgram t
                                   where t.sessionDate < :today or (t.sessionDate = :today and t.startTime <= :time))
            """)
    int markUnansweredOnceStarted(@Param("today") LocalDate today, @Param("time") LocalTime time,
                                  @Param("now") LocalDateTime now);
}
