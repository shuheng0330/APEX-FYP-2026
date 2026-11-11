package com.tbm.careerpathlearning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity @Getter @Setter
@Table(name = "appraisal_employee_insight_scope")
public class EmployeeInsightScope {
    @Id private Long participantId;
    @Column(nullable = false, updatable = false) private UUID employeeId;
    @Column(nullable = false, updatable = false) private Long reviewPeriodId;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false, updatable = false, length = 255) private String expectedDatabaseMarker;
    @Column(nullable = false, updatable = false, columnDefinition = "text") private String provenance;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private TrustRoots sourceRoots;

    public record TrustRoots(Map<String, Long> plans, Long attitudeConfigurationId,
            List<Long> historicalParticipantIds) {}
}
