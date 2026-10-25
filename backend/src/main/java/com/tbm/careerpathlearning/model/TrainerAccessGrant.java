package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Trainer access given to one staff member by the Super Admin, on top of whatever their job role allows
 * (FR-20-01). One row per person holds the current grant; the training audit log keeps every change.
 */
@Getter
@Setter
@Entity
@Table(name = "trainer_access_grant")
public class TrainerAccessGrant {

    @Id
    @Column(name = "staff_id")
    private UUID staffId;

    /** The last day the access works, in Malaysia time; null when it has no end date. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @Column(name = "granted_at", nullable = false)
    private OffsetDateTime grantedAt;

    /** When the Super Admin revoked it; a revoked grant no longer works. */
    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    /** When the daily job found its end date had passed and recorded the lapse. */
    @Column(name = "lapsed_at")
    private OffsetDateTime lapsedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** Whether the grant gives Trainer access on the given Malaysia date. */
    public boolean activeOn(LocalDate date) {
        return revokedAt == null && (endDate == null || !endDate.isBefore(date));
    }
}
