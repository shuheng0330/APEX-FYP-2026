package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Getter @Setter @Entity
@Table(name="attitude_assessment",uniqueConstraints=@UniqueConstraint(name="uq_attitude_assessment_participant",columnNames="participant_id"))
public class AttitudeAssessment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="participant_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ReviewPeriodParticipant participant;
    @Column(name="review_period_id",nullable=false,updatable=false) private Long reviewPeriodId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="configuration_id",nullable=false,updatable=false)
    private AttitudeConfiguration configuration;
    @Enumerated(EnumType.STRING) @Column(name="evaluation_format",nullable=false,updatable=false,length=20)
    private AttitudeEvaluationFormat evaluationFormat;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20)
    private AttitudeAssessmentStatus status=AttitudeAssessmentStatus.DRAFT;
    @Column(name="created_at",nullable=false,updatable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at",nullable=false) private OffsetDateTime updatedAt;
    @Column(name="created_by",nullable=false,updatable=false) private UUID createdBy;
    @Column(name="updated_by",nullable=false) private UUID updatedBy;
    @Column(name="submitted_at") private OffsetDateTime submittedAt;
    @Column(name="submitted_by") private UUID submittedBy;
    @Column(name="submitted_to_superior_id") private UUID submittedToSuperiorId;
    @Column(name="submitted_late") private Boolean submittedLate;
    @Column(name="reviewed_at") private OffsetDateTime reviewedAt;
    @Column(name="reviewed_by") private UUID reviewedBy;
    @Column(name="reviewed_late") private Boolean reviewedLate;
    @Column(name="attitude_score",precision=7,scale=4) private BigDecimal attitudeScore;
    @OneToMany(mappedBy="assessment",cascade=CascadeType.ALL) @OrderBy("id ASC")
    private List<AttitudeAssessmentItem> items=new ArrayList<>();
}
