package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.MaterialReviewAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One entry in a module's material review history: how it was generated, and who edited, approved or rejected it,
 * when and why, plus each AI regeneration (FR-07-14, NFR-007).
 */
@Getter
@Setter
@Entity
@Table(name = "sop_material_review_event")
public class SopMaterialReviewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sop_module_id", nullable = false)
    private Long sopModuleId;

    /** A {@link MaterialReviewAction} name, kept as plain text rather than an enum column. */
    @Column(name = "action", nullable = false, length = 20)
    private String action;

    /** How it was generated, what was edited, or the rejection reason. */
    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    /** The staff member who acted; null when the AI did. */
    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static SopMaterialReviewEvent of(Long moduleId, MaterialReviewAction action, String detail, UUID actorId,
                                            OffsetDateTime at) {
        SopMaterialReviewEvent event = new SopMaterialReviewEvent();
        event.setSopModuleId(moduleId);
        event.setAction(action.name());
        event.setDetail(detail);
        event.setActorId(actorId);
        event.setCreatedAt(at);
        return event;
    }
}
