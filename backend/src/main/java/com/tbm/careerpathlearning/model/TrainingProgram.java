package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionTiming;
import com.tbm.careerpathlearning.enums.TrainingType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ColumnDefault;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A training session on the Training Calendar (FR-12): a Training or Staff Sharing session on one day, delivered in
 * person, hybrid or online. Every date and time is Malaysia time ({@link com.tbm.careerpathlearning.config.ClockConfig#ZONE}).
 * <p>
 * Kept in the table of the old Training Programme pages, rebuilt in place. Their end date, public flag, notes,
 * check-in radius and competency links are no longer used.
 */
@Getter
@Setter
@Entity
@Table(name = "TRAINING_PROGRAM")
public class TrainingProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long trainingId;

    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    /** The column defaults fill any rows from the old pages when the columns are added. */
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'TRAINING'")
    @Column(name = "session_kind", nullable = false, length = 20)
    private TrainingSessionKind kind = TrainingSessionKind.TRAINING;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PHYSICAL'")
    @Column(name = "delivery", nullable = false, length = 20)
    private TrainingDelivery delivery = TrainingDelivery.PHYSICAL;

    /** Set for Physical and Hybrid training sessions only (FR-17-01). */
    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", length = 20)
    private TrainingType trainingType;

    /** The day of the session: sessions don't run over several days (FR-12-01). */
    @Column(name = "start_date")
    private LocalDate sessionDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    /** The room or address; for an online session, optionally the meeting link. */
    @Column(name = "venue", columnDefinition = "text")
    private String venue;

    /** Where the venue is on the map, when the trainer picked it there. */
    @Column(name = "location_name")
    private String locationName;

    private Double latitude;

    private Double longitude;

    private Integer capacity;

    @Column(name = "is_mandatory")
    private boolean mandatory;

    /** Who runs or presents the session: a staff member with Trainer access, or HR (FR-12-01, FR-15-05)... */
    @Column(name = "trainer_id")
    private UUID trainerId;

    /** ...or, for a training session only, an external trainer named by hand. */
    @Column(name = "trainer_name", length = 100)
    private String trainerName;

    /**
     * The target audience (FR-12-03): staff in any of these departments or job roles, or named. When none are
     * chosen, the session is for everyone.
     */
    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "training_program_org_chart", joinColumns = @JoinColumn(name = "training_id"))
    @Column(name = "org_chart_id")
    private Set<Long> departmentIds = new HashSet<>();

    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "training_target_role", joinColumns = @JoinColumn(name = "training_id"))
    @Column(name = "role_id")
    private Set<Long> roleIds = new HashSet<>();

    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "training_program_staff", joinColumns = @JoinColumn(name = "training_id"))
    @Column(name = "staff_id")
    private Set<UUID> staffIds = new HashSet<>();

    /** Staff can register themselves (FR-12-04). */
    @Column(name = "self_registration", nullable = false, columnDefinition = "boolean default true not null")
    private boolean selfRegistration = true;

    /** Registering asks for the HRDC details (FR-12-04, FR-13-03); otherwise it is a single step. */
    @Column(name = "registration_form", nullable = false, columnDefinition = "boolean default false not null")
    private boolean registrationForm;

    /** The last day staff can register, if there is one (FR-12-12). */
    @Column(name = "registration_cutoff")
    private LocalDate registrationCutoff;

    @Column(name = "waitlist_enabled", nullable = false, columnDefinition = "boolean default true not null")
    private boolean waitlistEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TrainingSessionStatus status = TrainingSessionStatus.SCHEDULED;

    /** Included in the notice to everyone registered (FR-12-06). */
    @Column(name = "cancel_reason", columnDefinition = "text")
    private String cancelReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    /** Deleted before anyone registered or was invited (FR-12-06): hidden everywhere. */
    @Column(name = "is_deleted")
    private boolean deleted;

    /** Who scheduled the session. They manage it, and so does HR. */
    private UUID createdBy;

    private LocalDateTime createdAt;

    private UUID updatedBy;

    private LocalDateTime updatedAt;

    public LocalDateTime startsAt() {
        return LocalDateTime.of(sessionDate, startTime);
    }

    public LocalDateTime endsAt() {
        return LocalDateTime.of(sessionDate, endTime);
    }

    /** The scheduled length, which is what attendees' training hours count (FR-17-04). */
    public int durationMinutes() {
        return (int) Duration.between(startTime, endTime).toMinutes();
    }

    public TrainingSessionTiming timing(LocalDateTime now) {
        return now.isBefore(startsAt()) ? TrainingSessionTiming.UPCOMING
                : now.isBefore(endsAt()) ? TrainingSessionTiming.IN_PROGRESS
                : TrainingSessionTiming.ENDED;
    }

    /** Whether a target audience was chosen; without one, the session is for everyone. */
    public boolean hasAudience() {
        return !departmentIds.isEmpty() || !roleIds.isEmpty() || !staffIds.isEmpty();
    }
}
