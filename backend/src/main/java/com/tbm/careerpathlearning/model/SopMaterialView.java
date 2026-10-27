package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** When a learner last opened a module's material, so a retake can require reviewing it first (FR-11-04). */
@Getter
@Setter
@Entity
@Table(name = "sop_material_view")
public class SopMaterialView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "sop_module_id", nullable = false)
    private Long sopModuleId;

    @Column(name = "viewed_at", nullable = false)
    private OffsetDateTime viewedAt;
}
