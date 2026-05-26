package com.tbm.careerpathlearning.repository;

import com.tbm.careerpathlearning.enums.Status;
import com.tbm.careerpathlearning.model.TrainingInvitation;
import com.tbm.careerpathlearning.model.TrainingProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TrainingInvitationRepository extends JpaRepository<TrainingInvitation, Long> {
    List<TrainingInvitation> findTrainingInvitationsByTrainingProgram(TrainingProgram trainingProgram);
    List<TrainingInvitation> findByStaff_Id(UUID staffId);

    @Query("""
        SELECT ti
        FROM TrainingInvitation ti
        WHERE  ti.trainingProgram.isDeleted = false
    """)
    List<TrainingInvitation> getActiveTrainingInvitations();

    @Query("""
        SELECT ti
        FROM TrainingInvitation ti
        WHERE ti.trainingProgram = :training
          AND ti.trainingProgram.isDeleted = false
    """)
    List<TrainingInvitation> findActiveByTrainingProgram(
            @Param("training") TrainingProgram trainingProgram
    );

    @Query("""
        SELECT ti
        FROM TrainingInvitation ti
        WHERE ti.staff.id = :staffId
          AND ti.trainingProgram.isDeleted = false
    """)
    List<TrainingInvitation> findActiveByStaffId(
            @Param("staffId") UUID staffId
    );

    @Modifying
    @Transactional
    @Query("""
    UPDATE TrainingInvitation i 
    SET i.status = :newStatus 
    WHERE i.status = :oldStatus 
    AND (
        i.trainingProgram.startDate < :currentDate
        OR 
        (i.trainingProgram.startDate = :currentDate AND i.trainingProgram.startTime <= :currentTime)
    )
""")
    int bulkUpdateExpiredInvites(
            @Param("newStatus") Status newStatus,
            @Param("oldStatus") Status oldStatus,
            @Param("currentDate") LocalDate currentDate,
            @Param("currentTime") LocalTime currentTime
    );
}
