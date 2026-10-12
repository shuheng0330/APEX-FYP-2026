package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A staff member's place on an SOP course: the terms they were assigned (or self-enrolled on an
 * Open course) and their progress. One per staff member per course; a new assignment updates it.
 */
@Getter
@Setter
@Entity
@Table(name = "sop_enrollment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sop_document_id", "staff_id"}))
public class SopEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sop_document_id", nullable = false)
    private Long sopDocumentId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    /** The latest assignment that set these terms; null when the staff member enrolled themselves. */
    @Column(name = "assignment_id")
    private Long assignmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private SopAssignmentMode mode;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** Null for a self-enrolled Open course with no deadline. */
    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "days_to_complete")
    private Integer daysToComplete;

    @Column(name = "pass_mark", nullable = false)
    private int passMark;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SopEnrollmentStatus status = SopEnrollmentStatus.NOT_STARTED;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    /** Final competency score, as a percentage, once the course is completed. */
    @Column(name = "final_score")
    private Integer finalScore;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * When the course must be finished: "start immediately" counts the days from the effective
     * date, "portal" from the day the staff member starts; never later than the deadline.
     */
    public LocalDate dueDate() {
        LocalDate from = mode == SopAssignmentMode.IMMEDIATE ? effectiveDate
                : startedAt != null ? startedAt.atZoneSameInstant(ClockConfig.ZONE).toLocalDate() : null;
        if (from == null || daysToComplete == null) {
            return deadline;
        }
        LocalDate window = from.plusDays(daysToComplete);
        return deadline != null && deadline.isBefore(window) ? deadline : window;
    }

    /** The SOP certificate's number (FR-11-03), stable for this enrolment. */
    public String certificateNo() {
        return String.format("APEX-SOP-%d-%05d", sopDocumentId, id);
    }
}
