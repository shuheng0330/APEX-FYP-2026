package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.enums.SopVisibility;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * An uploaded SOP document and the state of its AI generation (FR-08).
 */
@Getter
@Setter
@Entity
@Table(name = "sop_document")
public class SopDocument {

    /** The pass mark a new course starts with (FR-07-15). */
    public static final int DEFAULT_PASS_MARK = 70;

    /** The estimated duration counted until the trainer sets the course's own (FR-07-15). */
    public static final int DEFAULT_DURATION_MINUTES = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String version;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "department_tag")
    private String departmentTag;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    /** FR-09-04; null is treated as PRIVATE (courses created before visibility existed). */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility")
    private SopVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "generation_status", nullable = false)
    private SopGenerationStatus generationStatus = SopGenerationStatus.PENDING;

    @Column(name = "status_message", columnDefinition = "text")
    private String statusMessage;

    /** Plain text extracted from the uploaded document, used as the AI prompt source. */
    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    /**
     * The course's own pass mark, as a percentage (FR-07-15); an assignment may raise it but not lower it
     * (FR-09-03). The column default fills existing courses when the column is added.
     */
    @Column(name = "pass_mark", nullable = false, columnDefinition = "integer default 70 not null")
    private int passMark = DEFAULT_PASS_MARK;

    /** The trainer's estimate of how long the course takes, in minutes; null until they set one (FR-07-15). */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** The duration that counts: the trainer's estimate, or the default until they set one. */
    public int effectiveDurationMinutes() {
        return durationMinutes != null ? durationMinutes : DEFAULT_DURATION_MINUTES;
    }
}
