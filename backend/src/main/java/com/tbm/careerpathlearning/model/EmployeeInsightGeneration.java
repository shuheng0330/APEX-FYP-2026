package com.tbm.careerpathlearning.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.tbm.careerpathlearning.dto.AppraisalInsightDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Getter @Setter
@Table(name = "appraisal_employee_insight_generation", uniqueConstraints = {
        @UniqueConstraint(name = "uq_employee_insight_active", columnNames = "active_context"),
        @UniqueConstraint(name = "uq_employee_insight_version", columnNames = {"participant_id", "insight_version"})})
public class EmployeeInsightGeneration {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private Long participantId;
    @Column(nullable = false, updatable = false) private UUID employeeId;
    @Column(nullable = false, updatable = false) private Long reviewPeriodId;
    @Column(nullable = false, updatable = false) private UUID ownerId;
    @Column(nullable = false, updatable = false, length = 64) private String fingerprint;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private AppraisalInsightDto.Evidence evidence;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private JsonNode sourceReferences;
    @Column(nullable = false, updatable = false, length = 100) private String model;
    @Column(nullable = false, updatable = false, length = 32) private String templateVersion;
    @Column(nullable = false, updatable = false, length = 24) private String dataMode = "FICTIONAL_UAT_EMPLOYEE";
    @Column(nullable = false, length = 16) private String status;
    private Long activeContext;
    private Integer insightVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private AppraisalInsightDto.Summary summary;
    @Column(nullable = false, updatable = false) private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private OffsetDateTime dispatchedAt;
    @Column(nullable = false) private LocalDate quotaDay;
    private OffsetDateTime retryAt;
    private OffsetDateTime leaseUntil;
    @Column(length = 32) private String errorCode;
    @Version private long lockVersion;
}
