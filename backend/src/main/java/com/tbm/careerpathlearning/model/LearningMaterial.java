package com.tbm.careerpathlearning.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name="LEARNING_MATERIAL")
public class LearningMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long materialId;

    private String title;

    private String description;

    @Column(name = "learning_outcomes", columnDefinition = "TEXT[]")
    private List<String> learningOutcomes;

    @Column(name = "material_type", columnDefinition = "text[]")
    private List<String> materialType;

    private Double totalDurationAllDoc;

    @OneToMany(
            mappedBy = "learningMaterial",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @SQLRestriction("org_chart_id IN (SELECT oc.id FROM org_chart oc WHERE oc.is_deleted = false)")
    private List<LearningMaterialOrgChart> departments;

    @OneToMany(mappedBy = "learningMaterial", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @ToString.Exclude
    private List<LearningDocument> learningDocuments;

    @ManyToMany
    @JoinTable( name = "learning_material_competency", joinColumns = @JoinColumn(name = "material_id"), inverseJoinColumns = @JoinColumn( name = "competency_id"))
    @org.hibernate.annotations.SQLRestriction("is_deleted = false")
    private List<Competency> competency;

    private UUID createdBy;

    private LocalDateTime createdAt;

    private UUID updatedBy;

    private LocalDateTime updatedAt;
}
