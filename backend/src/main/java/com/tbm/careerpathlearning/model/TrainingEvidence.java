package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Evidence of attendance attached to a session (FR-14-08), such as a group photo or a signed attendance sheet. The
 * file is stored under the app's upload folder with a generated name; the person's own file name is kept for display.
 */
@Getter
@Setter
@Entity
@Table(name = "training_evidence", indexes = {
        @Index(name = "idx_training_evidence_session", columnList = "training_id")
})
public class TrainingEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long evidenceId;

    @Column(name = "training_id", nullable = false)
    private Long trainingId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** Relative to the upload folder. */
    @Column(name = "stored_path", nullable = false)
    private String storedPath;

    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
