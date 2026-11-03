package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One entry in the training audit log (NFR-007): who did what to which record, when and why.
 * Entries are only ever added, never changed or deleted.
 */
@Getter
@Setter
@Entity
@Table(name = "training_audit_log", indexes = {
        @Index(name = "idx_training_audit_target", columnList = "target_type, target_id")
})
public class TrainingAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** A {@link com.tbm.careerpathlearning.enums.TrainingAuditAction} name. */
    @Column(name = "action", nullable = false, length = 60)
    private String action;

    /** A {@link com.tbm.careerpathlearning.enums.TrainingAuditTarget} name; with targetId, what the action applied to. */
    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    /** Who acted; null when a scheduled job did. */
    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    /** Other facts worth keeping, such as a new end date, in plain words. */
    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
