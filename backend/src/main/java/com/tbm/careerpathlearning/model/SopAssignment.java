package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One "Assign" action by a trainer: a course given to an audience with its terms. Kept as the
 * assignment history (FR-09-08); each recipient gets a {@link SopEnrollment}.
 */
@Getter
@Setter
@Entity
@Table(name = "sop_assignment")
public class SopAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sop_document_id", nullable = false)
    private Long sopDocumentId;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    /** Who it was assigned to, as shown in the history: "Everyone", or departments, roles and names. */
    @Column(name = "audience", columnDefinition = "text", nullable = false)
    private String audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false)
    private SopAssignmentMode mode;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    /** The date the course becomes available to the audience. */
    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    /** No completion is accepted after this date. */
    @Column(name = "deadline", nullable = false)
    private LocalDate deadline;

    /** Each person has this many days to finish, counted from the start (FR-09-03). */
    @Column(name = "days_to_complete", nullable = false)
    private int daysToComplete;

    /** Minimum quiz score, as a percentage, to pass (FR-09-03). */
    @Column(name = "pass_mark", nullable = false)
    private int passMark;

    /** Staff newly assigned or whose terms were updated by this assignment. */
    @Column(name = "staff_count", nullable = false)
    private int staffCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
