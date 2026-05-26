package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name="staff_learning_material")

public class StaffLearningMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long enrollmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    private LearningMaterial learningMaterial;

    private LocalDateTime enrolledAt;

    private LocalDateTime completedAt;

    private Double progress;

    private boolean isCompleted = false;

    private UUID enrolledBy;

//    private String lastPosition; // e.g. video timestamp in seconds, or PDF page number
}
