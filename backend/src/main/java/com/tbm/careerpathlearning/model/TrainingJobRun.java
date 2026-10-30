package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A completed daily run of the training jobs, so each day's run happens at most once. */
@Getter
@Setter
@Entity
@Table(name = "training_job_run", uniqueConstraints = {
        @UniqueConstraint(name = "uk_training_job_run_day", columnNames = {"job_name", "run_date"})
})
public class TrainingJobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 60)
    private String jobName;

    /** The Malaysia date the run was for. */
    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private OffsetDateTime finishedAt;

    /** What the run did, in plain words. */
    @Column(name = "outcome", columnDefinition = "text")
    private String outcome;
}
