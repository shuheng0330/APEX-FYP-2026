package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "staff_learning_document_progress")
public class StaffLearningDocumentProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enrollment_id")
    private StaffLearningMaterial staffLearningMaterial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private LearningDocument learningDocument;

    private Double progress;

    private String lastPosition;

    private LocalDateTime lastAccessedAt;

    @Column(name = "is_completed")
    private Boolean isCompleted;
}
