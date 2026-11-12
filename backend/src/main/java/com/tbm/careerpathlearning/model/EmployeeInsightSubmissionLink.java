package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Getter @Setter @Immutable
@Table(name = "appraisal_employee_insight_submission_link")
public class EmployeeInsightSubmissionLink {
    @Id private Long submissionActivityId;
    @Column(nullable = false, updatable = false) private Long appraisalId;
    @Column(nullable = false, updatable = false) private UUID generationId;
    @Column(nullable = false, updatable = false) private Long participantId;
    @Column(nullable = false, updatable = false) private OffsetDateTime createdAt;
}
