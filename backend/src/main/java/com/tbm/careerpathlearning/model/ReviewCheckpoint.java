package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.ReviewFrequency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "review_checkpoint", uniqueConstraints =
        @UniqueConstraint(name = "uq_review_checkpoint_sequence",
                columnNames = {"review_period_id", "review_frequency", "sequence_number"}))
public class ReviewCheckpoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_period_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_checkpoint_period"))
    private AnnualKpiReviewPeriod reviewPeriod;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_frequency", nullable = false, length = 20)
    private ReviewFrequency reviewFrequency;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "self_assessment_deadline", nullable = false)
    private LocalDate selfAssessmentDeadline;

    @Column(name = "superior_assessment_deadline", nullable = false)
    private LocalDate superiorAssessmentDeadline;
}
