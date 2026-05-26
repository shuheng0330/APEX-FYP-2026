package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.Status;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "training_invitation", indexes = {
        @Index(name = "idx_invite_status_fk", columnList = "status, training_id")
})
public class TrainingInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long invitationId;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String reason;

    private LocalDateTime invitedAt;

    private LocalDateTime respondAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "training_id")
    private TrainingProgram trainingProgram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by", nullable = false)
    private Staff invitedBy;


}
