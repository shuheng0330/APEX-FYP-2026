package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.TrainingRegistrationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.UUID;

/** A staff member's place on a session (FR-13): registered, waitlisted, offered a freed seat, or cancelled. */
@Getter
@Setter
@Entity
@Table(name = "TRAINING_REGISTRATION")
public class TrainingRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long registrationId;

    @Column(name = "training_id", nullable = false)
    private Long trainingId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'REGISTERED'")
    @Column(name = "status", nullable = false, length = 20)
    private TrainingRegistrationStatus status = TrainingRegistrationStatus.REGISTERED;

    private LocalDateTime registeredAt;
}
