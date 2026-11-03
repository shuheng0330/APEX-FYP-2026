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
    /** The course was archived; its records stay on the Scoreboard (FR-06-14). */
    private boolean courseArchived;
    private int versionNo;
    /** A newer version replaced this one; its records stay on the Scoreboard (FR-07-17). */
    private boolean courseSuperseded;
    private int moduleCount;
    private int modulesPassed;
    /**
     * NOT_STARTED, IN_PROGRESS, RETRAINING (a module's quiz was failed and is not passed yet,
     * FR-10-04), COMPLETED, or WITHDRAWN (the course was archived before they finished, FR-06-15).
     */
    private String status;
    private boolean overdue;
    private boolean mandatory;
    private SopAssignmentMode mode;
    private boolean selfEnrolled;
    /** The person has left the company: listed, but not counted in the compliance figures (FR-10-12). */
    private boolean leftCompany;
    /** The viewer may open the person's whole training record: HR, the Super Admin, or their manager (FR-18-01). */
    private boolean canOpenRecord;
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
