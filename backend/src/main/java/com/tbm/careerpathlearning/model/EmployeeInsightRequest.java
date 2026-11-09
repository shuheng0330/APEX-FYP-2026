package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Getter @Setter @Immutable
@Table(name = "appraisal_employee_insight_request", uniqueConstraints =
        @UniqueConstraint(name = "uq_employee_insight_request", columnNames = {"actor_id", "request_key"}))
public class EmployeeInsightRequest {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID actorId;
    @Column(nullable = false, updatable = false) private UUID requestKey;
    @Column(nullable = false, updatable = false) private Long participantId;
    @Column(nullable = false, updatable = false, length = 16) private String operation;
    @Column(nullable = false, updatable = false) private UUID generationId;
    @Column(nullable = false, updatable = false, length = 24) private String disposition;
    @Column(nullable = false, updatable = false) private OffsetDateTime createdAt;
}
