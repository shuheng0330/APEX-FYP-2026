package com.tbm.careerpathlearning.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "learning_document")
public class LearningDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long documentId;

    private String title;

    private String fileUrl;

    private Integer totalPages;

    private Double totalDuration;

    private String fileType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    @JsonIgnore
    @ToString.Exclude
    private LearningMaterial learningMaterial;

    private LocalDateTime createdAt;
}
