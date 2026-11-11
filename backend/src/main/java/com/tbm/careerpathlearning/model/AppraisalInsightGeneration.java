package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.dto.AppraisalInsightDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Getter @Setter
@Table(name = "appraisal_insight_generation", uniqueConstraints = {
        @UniqueConstraint(name = "uq_insight_request", columnNames = {"ownerId", "requestKey"}),
        @UniqueConstraint(name = "uq_insight_active", columnNames = "activeKey")})
public class AppraisalInsightGeneration {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID ownerId;
    @Column(nullable = false, updatable = false) private UUID requestKey;
    @Column(nullable = false, updatable = false, length = 40) private String scenario;
    @Column(nullable = false, updatable = false, length = 24) private String dataMode = "SYNTHETIC_ONLY";
    @Column(nullable = false, updatable = false, length = 32) private String fixtureVersion;
    @Column(nullable = false, updatable = false, length = 32) private String templateVersion;
    @Column(nullable = false, updatable = false, length = 100) private String model;
    @Column(nullable = false, updatable = false, length = 64) private String evidenceHash;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private AppraisalInsightDto.Evidence evidence;
    @Column(nullable = false, length = 16) private String status;
    @Column(length = 180) private String activeKey;
    @Column(length = 32) private String errorCode;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private AppraisalInsightDto.Summary summary;
    @Column(nullable = false, updatable = false) private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    @Version private Long lockVersion;
}
