package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A course on the learner's Learning Portal: one they are enrolled in, or an Open course they can join. */
@Data
public class SopLearningCourseDto {
    private Long sopDocumentId;
    private String title;
    private String version;
    /** The SOP's department, used as the course category (FR-11-09). */
    private String category;
    private int moduleCount;

    /** Null for an Open course the learner has not enrolled in. */
    private Long enrollmentId;
    private SopEnrollmentStatus status;
    private SopAssignmentMode mode;
    private boolean mandatory;
    /** Enrolled from the Open courses list rather than assigned. */
    private boolean selfEnrolled;
    private LocalDate effectiveDate;
    private LocalDate dueDate;
    private LocalDate deadline;
    private Integer daysToComplete;
    private int passMark;
    private int modulesPassed;
    /** Some module was failed and is waiting to be retaken (FR-10-04). */
    private boolean retraining;
    /** Not completed and past its due date. */
    private boolean overdue;
    /**
     * The course can be worked on: its effective date has arrived, the learner was not withdrawn, and the course
     * is not archived.
     */
    private boolean available;
    /** The course was archived (FR-06-14): it can't be taken any more, but results and certificates are kept. */
    private boolean courseArchived;
    /** The course version this enrolment is for (FR-18-05). */
    private int versionNo;
    /** A newer version replaced this one (FR-07-17): it can't be taken any more, but results are kept. */
    private boolean courseSuperseded;
    private Integer finalScore;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
