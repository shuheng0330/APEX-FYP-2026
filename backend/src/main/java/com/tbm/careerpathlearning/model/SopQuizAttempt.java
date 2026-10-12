package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One submission of a module quiz by a learner (FR-11-02). A score below the enrollment's pass
 * mark is a failed attempt, which puts the module into retraining (FR-10-04, FR-10-05).
 */
@Getter
@Setter
@Entity
@Table(name = "sop_quiz_attempt")
public class SopQuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "sop_module_id", nullable = false)
    private Long sopModuleId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "correct", nullable = false)
    private int correct;

    @Column(name = "total", nullable = false)
    private int total;

    /** Percentage of questions answered correctly, 0-100. */
    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    /** JSON object of question id to the answer given. */
    @Column(name = "answers", columnDefinition = "text")
    private String answers;

    @Column(name = "attempted_at", nullable = false)
    private OffsetDateTime attemptedAt;
}
