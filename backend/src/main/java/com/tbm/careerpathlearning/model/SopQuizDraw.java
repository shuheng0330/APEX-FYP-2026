package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * The questions a learner's current attempt at a module quiz was given, drawn at random (FR-11-14). The attempt is
 * marked on exactly these, and the draw is removed once it is submitted.
 */
@Getter
@Setter
@Entity
@Table(name = "sop_quiz_draw")
public class SopQuizDraw {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "sop_module_id", nullable = false)
    private Long sopModuleId;

    /** The drawn question ids, comma separated, in the order they were shown. */
    @Column(name = "question_ids", nullable = false, columnDefinition = "text")
    private String questionIds;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
