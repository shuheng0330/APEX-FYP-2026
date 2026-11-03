package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.*;
import com.tbm.careerpathlearning.dto.KpiItemDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Getter @Setter @Table(name="kpi_plan_version")
public class KpiPlanVersion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="plan_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private KpiPlan plan;
    @Column(nullable=false,updatable=false) private Long reviewPeriodId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=20) private KpiLevel level;
    @Column(nullable=false,updatable=false) private int versionNumber;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="base_version_id",updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT)) private KpiPlanVersion baseVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private KpiVersionStatus status=KpiVersionStatus.DRAFT;
    @Column(columnDefinition="text",updatable=false) private String revisionReason;
    @Column(nullable=false,updatable=false) private OffsetDateTime createdAt;
    @Column(nullable=false) private OffsetDateTime updatedAt;
    @Column(nullable=false,updatable=false) private UUID createdBy;
    @Column(nullable=false) private UUID updatedBy;
    private UUID requestedBy;
    @Enumerated(EnumType.STRING) @Column(length=20,updatable=false)
    private KpiRevisionPreparationMode preparationMode;
    @Column(updatable=false) private UUID revisionSuperiorId;
    private UUID preparedBy;
    private OffsetDateTime preparedAt;
    private OffsetDateTime submittedAt;
    private UUID approvedBy;
    private OffsetDateTime approvedAt;
    private UUID confirmedBy;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime effectiveAt;
    private UUID cancelledBy;
    private OffsetDateTime cancelledAt;
    @Column(columnDefinition="text") private String cancellationReason;
    @Enumerated(EnumType.STRING) @Column(length=32) private KpiVersionStatus cancelledFromState;
    @Column(columnDefinition="text") private String latestReturnReason;
    private UUID latestReturnedBy;
    private OffsetDateTime latestReturnedAt;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition="jsonb") private List<KpiItemDto> latestReturnItems;
    @Column(nullable=false) private boolean revisionRequired;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(columnDefinition="jsonb",nullable=false)
    private List<com.tbm.careerpathlearning.dto.KpiRevisionDecisionDto> decisionHistory=new ArrayList<>();
    @Version private long lockVersion;
    @OneToMany(mappedBy="version",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("id ASC")
    private List<Kpi> items=new ArrayList<>();
}
