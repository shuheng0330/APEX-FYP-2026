package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.SopGenerationStatus;
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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
