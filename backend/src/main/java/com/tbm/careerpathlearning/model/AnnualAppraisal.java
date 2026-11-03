package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.dto.AnnualAppraisalSnapshotDto;
import com.tbm.careerpathlearning.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Getter @Setter
@Table(name="annual_appraisal", uniqueConstraints=@UniqueConstraint(name="uq_annual_appraisal_participant",columnNames="participant_id"))
public class AnnualAppraisal {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="participant_id",nullable=false,updatable=false)
    private ReviewPeriodParticipant participant;
    @Column(nullable=false,updatable=false) private Long reviewPeriodId;
    @Column(nullable=false,updatable=false) private UUID employeeId;
    @Column(nullable=false,updatable=false) private UUID superiorId;
    @Enumerated(EnumType.STRING) @Column(length=20,nullable=false) private AppraisalStatus status=AppraisalStatus.DRAFT;
    @Enumerated(EnumType.STRING) @Column(length=24) private AnnualAppraisalRecommendation recommendation;
    @Column(columnDefinition="text") private String justification;
    @Enumerated(EnumType.STRING) @Column(length=24) private AnnualAppraisalRecommendation finalDecision;
    @Column(columnDefinition="text") private String overrideReason;
    @Column(columnDefinition="text") private String latestReturnReason;
    @Enumerated(EnumType.STRING) @Column(length=24) private AnnualAppraisalRecommendation returnedRecommendation;
    @Column(columnDefinition="text") private String returnedJustification;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private AnnualAppraisalSnapshotDto snapshot;
    @Column(nullable=false,updatable=false) private OffsetDateTime createdAt;
    @Column(nullable=false) private OffsetDateTime updatedAt;
    @Column(nullable=false,updatable=false) private UUID createdBy;
    @Column(nullable=false) private UUID updatedBy;
    private OffsetDateTime submittedAt;
    private Boolean submittedLate;
    private OffsetDateTime reviewedAt;
    private UUID reviewedBy;
    private Boolean reviewedLate;
    @Version private long lockVersion;
}
