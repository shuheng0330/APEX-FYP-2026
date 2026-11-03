package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.dto.AnnualAppraisalSnapshotDto;
import com.tbm.careerpathlearning.enums.AnnualAppraisalRecommendation;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Getter @Setter @Table(name="annual_appraisal_activity")
public class AnnualAppraisalActivity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long appraisalId;
    @Column(nullable=false,updatable=false,length=24) private String action;
    @Column(nullable=false,updatable=false) private UUID actorId;
    @Column(nullable=false,updatable=false) private OffsetDateTime occurredAt;
    @Column(updatable=false,columnDefinition="text") private String reason;
    @Enumerated(EnumType.STRING) @Column(updatable=false,length=24) private AnnualAppraisalRecommendation recommendation;
    @Column(updatable=false,columnDefinition="text") private String justification;
    @Enumerated(EnumType.STRING) @Column(updatable=false,length=24) private AnnualAppraisalRecommendation finalDecision;
    @JdbcTypeCode(SqlTypes.JSON) @Column(updatable=false,columnDefinition="jsonb") private AnnualAppraisalSnapshotDto snapshot;
}
