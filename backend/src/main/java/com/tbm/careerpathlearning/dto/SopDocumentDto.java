package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.SopCourseStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class SopDocumentDto {
    private Long id;
    private String title;
    private String version;
    private String originalFilename;
    private String departmentTag;
    private SopGenerationStatus generationStatus;
    private String statusMessage;
    private int moduleCount;
    private OffsetDateTime createdAt;

    /** Preparer shown on the material (FR-07-07); the course's owner. */
    private String preparedByName;

    /** Whether the signed-in person may change this course: only its owner can (FR-07-08, FR-07-19). */
    private boolean editable;

    /** The course's own pass mark, as a percentage (FR-07-15). */
    private int passMark;

    /** The estimated duration that counts, in minutes: the trainer's, or the default until they set one. */
    private int durationMinutes;

    /** Whether the trainer has set the estimated duration; until then the default counts and they are reminded. */
    private boolean durationSet;

    /** Live or Archived (FR-06-14); Draft and Superseded arrive with versions. */
    private SopCourseStatus courseStatus;

    /** When it was archived; null unless it is archived. */
    private OffsetDateTime archivedAt;

    /** Everyone ever enrolled, in any status. A course anyone has enrolled in is archived, never deleted. */
    private int enrolledCount;

    /** Staff still taking it, not started or in progress: archiving withdraws them (FR-06-15). */
    private int activeEnrollmentCount;
}
