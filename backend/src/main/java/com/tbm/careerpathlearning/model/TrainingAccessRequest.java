package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A staff member's request for Trainer access, made after attending a Product Training session, to present a staff
 * sharing session (FR-15-06). One per person per session; the Super Admin grants the access on the Trainer Access page.
 */
@Getter
@Setter
@Entity
@Table(name = "training_access_request") // one per person per session: patch P011
public class TrainingAccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    /** The Product Training session it was asked from. */
    @Column(name = "training_id", nullable = false)
    private Long trainingId;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
}
