package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.TrainingInvitationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** An invitation to register for a mandatory session, sent to its target staff (FR-12-14). */
@Getter
@Setter
@Entity
@Table(name = "training_invitation", indexes = {
        @Index(name = "idx_invite_status_fk", columnList = "status, training_id")
})
public class TrainingInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long invitationId;

    @Column(name = "training_id", nullable = false)
    private Long trainingId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TrainingInvitationStatus status = TrainingInvitationStatus.PENDING;

    /** Why the person declined. */
    private String reason;

    private LocalDateTime invitedAt;

    /** When the person answered, or when the session started unanswered. */
    @Column(name = "respond_at")
    private LocalDateTime respondedAt;
}
