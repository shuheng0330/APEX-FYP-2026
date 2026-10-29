package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.SopCourseStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SopDocumentDto {
    private Long id;
    private String title;
    private String version;
    private String originalFilename;
    private String departmentTag;
    /** The course's categories, primary first (FR-19-05). */
    private List<CourseCategoryChipDto> categories = new ArrayList<>();
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

    /** Its place among the documents waiting to be generated, 1 for the next; 0 when it isn't waiting (FR-06-11). */
    private int queuePosition;

    /** The id every version of this course shares (FR-07-16). */
    private Long courseId;

    /** 1, 2, 3 and so on. */
    private int versionNo;

    /** The draft of the course's next version, if one is in progress. */
    private Long draftId;

    /** The course's live version, if it has one. */
    private Long liveVersionId;
}
