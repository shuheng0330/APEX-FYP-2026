package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "EVALUATION_RATINGS")
public class EvaluationRatings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long evaluationRatingId;

    @ManyToOne
    @JoinColumn(name = "evaluation_id")
    private Evaluation evaluation;

    @ManyToOne
    @JoinColumn(name = "comp_id")
    private Competency competency;

    private Integer rating;

    private LocalDate createdAt;
}
