package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One staff member's enrolment in one course, as the Training Scoreboard lists it (FR-10-01). */
@Data
public class SopScoreboardRecordDto {
    private Long enrollmentId;
    private UUID staffId;
    private String staffName;
    private String jobRole;
    private String department;
    private Long sopDocumentId;
    private String courseTitle;
    private int moduleCount;
    private int modulesPassed;
    /**
     * NOT_STARTED, IN_PROGRESS, RETRAINING (a module's quiz was failed and is not passed yet,
     * FR-10-04) or COMPLETED.
     */
    private String status;
    private boolean overdue;
    private boolean mandatory;
    private SopAssignmentMode mode;
    private boolean selfEnrolled;
    private int passMark;
    /** The final score once completed, otherwise the average of the modules passed so far. */
    private Integer score;
    private Integer finalScore;
    private int attempts;
    /** Failed quiz attempts, each of which sent a module back for retraining (FR-10-05). */
    private int retrainingAttempts;
    private OffsetDateTime lastAttemptAt;
    private LocalDate dueDate;
    private OffsetDateTime assignedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
