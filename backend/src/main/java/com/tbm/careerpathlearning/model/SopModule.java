package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A generated, sequential training module belonging to an SOP (FR-09).
 */
@Getter
@Setter
@Entity
@Table(name = "sop_module")
public class SopModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sop_document_id", nullable = false)
    private Long sopDocumentId;

    /** 1-based order; modules must be completed in sequence (FR-09-01). */
    @Column(name = "module_order", nullable = false)
    private int moduleOrder;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
