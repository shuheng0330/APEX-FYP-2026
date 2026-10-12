package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A learner's rating and comment on an SOP course they completed (FR-11-06). One per learner per
 * course; submitting again updates it.
 */
@Getter
@Setter
@Entity
@Table(name = "sop_course_feedback",
        uniqueConstraints = @UniqueConstraint(columnNames = {"sop_document_id", "staff_id"}))
public class SopCourseFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sop_document_id", nullable = false)
    private Long sopDocumentId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    /** 1 to 5 stars. */
    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
