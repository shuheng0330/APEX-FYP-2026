package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.QuizReviewAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One entry in a module quiz's review history: who approved, rejected or edited it,
 * when and why, plus each AI regeneration (FR-08-09, NFR-007).
 */
@Getter
@Setter
@Entity
@Table(name = "sop_quiz_review_event")
public class SopQuizReviewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sop_module_id", nullable = false)
    private Long sopModuleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private QuizReviewAction action;

    /** The rejection reason, or what was edited or regenerated. */
    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    /** The staff member who acted; null when the AI regenerated the quiz. */
    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
