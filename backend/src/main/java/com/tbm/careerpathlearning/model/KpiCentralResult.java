package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Getter @Setter @Table(name="kpi_central_result")
public class KpiCentralResult {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long reviewPeriodId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="checkpoint_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ReviewCheckpoint checkpoint;
    @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=20) private ReviewFrequency reviewFrequency;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="plan_version_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private KpiPlanVersion planVersion;
    @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=20) private KpiLevel level;
    @Column(nullable=false,updatable=false) private int editionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private KpiCentralResultStatus status=KpiCentralResultStatus.DRAFT;
    @Column(updatable=false) private Long previousEditionId;
    @Column(columnDefinition="text") private String correctionReason;
    @Column(nullable=false,updatable=false) private OffsetDateTime createdAt;
    @Column(nullable=false) private OffsetDateTime updatedAt;
    @Column(nullable=false,updatable=false) private UUID createdBy;
    @Column(nullable=false) private UUID updatedBy;
    private OffsetDateTime publishedAt;
    private UUID publishedBy;
    @Version private long lockVersion;
    @OneToMany(mappedBy="result",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("id ASC")
    private List<KpiCentralResultItem> items=new ArrayList<>();
}
