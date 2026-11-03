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

    /** When the person registered or joined the waitlist: the waitlist is served in this order (FR-13-07). */
    private LocalDateTime registeredAt;

    /** A freed seat held for this person, until they take it up or the offer runs out (FR-13-07). */
    @Column(name = "offered_at")
    private LocalDateTime offeredAt;

    @Column(name = "offer_expires_at")
    private LocalDateTime offerExpiresAt;

    /** The trainer or HR who added the person; null when they registered themselves. */
    @Column(name = "added_by")
    private UUID addedBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    /** Why the registration ended, in plain words: cancelled, removed, an offer declined or not taken up. */
    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    /*
     * The HRDC registration details (FR-13-03), kept only for sessions that ask for the form and erased when the
     * registration is cancelled (decision C). Shown only to the session's trainer and HR; never in notifications.
     */
    @Column(name = "id_number", length = 30)
    private String idNumber;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "branch", length = 60)
    private String branch;

    /** When the person agreed to the PDPA notice. */
    @Column(name = "pdpa_consent_at")
    private LocalDateTime pdpaConsentAt;

    /** Removes the HRDC details, which are not kept once the person no longer has a place (decision C). */
    public void eraseForm() {
        idNumber = null;
        phone = null;
        branch = null;
        pdpaConsentAt = null;
    }
}
