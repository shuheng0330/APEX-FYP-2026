package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Getter @Setter
@Table(name = "kpi_plan", uniqueConstraints = @UniqueConstraint(name="uq_kpi_plan_id_period", columnNames={"id","review_period_id"}))
public class KpiPlan {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="review_period_id", nullable=false, updatable=false)
    private AnnualKpiReviewPeriod reviewPeriod;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20, updatable=false) private KpiLevel level;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="department_id", updatable=false) private OrgChart department;
    @Column(name="owner_participant_id", updatable=false) private Long ownerParticipantId;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="owner_participant_id", insertable=false, updatable=false, foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ReviewPeriodParticipant ownerParticipant;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private KpiPlanStatus status = KpiPlanStatus.DRAFT;
    @OneToMany(mappedBy="plan", cascade=CascadeType.ALL, orphanRemoval=true) @OrderBy("id ASC")
    private List<Kpi> items = new ArrayList<>();
    @Column(name="created_at", nullable=false, updatable=false) private OffsetDateTime createdAt;
    @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;
    @Column(name="created_by", nullable=false, updatable=false) private UUID createdBy;
    @Column(name="updated_by", nullable=false) private UUID updatedBy;
    @Column(name="published_at") private OffsetDateTime publishedAt;
    @Column(name="published_by") private UUID publishedBy;
    @Column(name="published_late") private Boolean publishedLate;
    private OffsetDateTime submittedAt;
    @Column(name="submitted_by") private UUID submittedBy;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="submitted_by", insertable=false, updatable=false, foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Staff submitter;
    @Column(name="submitted_to_superior_id") private UUID submittedToSuperiorId;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="submitted_to_superior_id", insertable=false, updatable=false, foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Staff submittedToSuperior;
    private Boolean submittedLate;
    private OffsetDateTime reviewedAt;
    private UUID reviewedBy;
    private Boolean reviewedLate;
    @Column(columnDefinition="text") private String returnReason;
    @Column(name="revision_required",nullable=false) private boolean revisionRequired;
    @Column(name="assistance_authorization_id",unique=true,updatable=false) private Long assistanceAuthorizationId;
}
