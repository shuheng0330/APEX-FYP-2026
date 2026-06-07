package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * A generated quiz question attached to a module (FR-10).
 * Reviewed via a gate separate from the material review (FR-10-02).
 */
@Getter
@Setter
@Entity
@Table(name = "sop_quiz_question")
public class SopQuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sop_module_id", nullable = false)
    private Long sopModuleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuizQuestionType questionType;

    @Column(name = "question_text", columnDefinition = "text", nullable = false)
    private String questionText;

    /** JSON array of option strings for MULTIPLE_CHOICE; null otherwise. */
    @Column(name = "options", columnDefinition = "text")
    private String options;

    @Column(name = "correct_answer", columnDefinition = "text")
    private String correctAnswer;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
