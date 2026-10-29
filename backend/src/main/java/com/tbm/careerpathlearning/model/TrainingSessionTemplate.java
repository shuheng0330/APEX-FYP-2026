package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A session's set-up saved for the next time the same training runs (FR-12-08 to FR-12-10): everything on the
 * scheduling form except the date and the registration cut-off. Each trainer sees their own templates; HR sees all.
 */
@Getter
@Setter
@Entity
@Table(name = "training_session_template")
public class TrainingSessionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long templateId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * The scheduling form as it was filled in, as JSON. Kept whole rather than column by column, so a template keeps
     * working when the form gains a field.
     */
    @Column(name = "setup", nullable = false, columnDefinition = "text")
    private String setup;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Sessions actually scheduled from it (FR-12-09): opening it on the form doesn't count. */
    @Column(name = "times_used", nullable = false, columnDefinition = "integer default 0 not null")
    private int timesUsed;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
}
