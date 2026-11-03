package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Getter @Setter @Table(name="kpi_assessment_evidence")
public class KpiAssessmentEvidence {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="item_id",nullable=false,updatable=false)
    private KpiAssessmentItem item;
    @Column(name="storage_key",nullable=false,unique=true,length=100,updatable=false) private String storageKey;
    @Column(name="original_filename",nullable=false,length=255,updatable=false) private String originalFilename;
    @Column(name="content_type",nullable=false,length=100,updatable=false) private String contentType;
    @Column(name="size_bytes",nullable=false,updatable=false) private Long sizeBytes;
    @Column(name="uploaded_by",nullable=false,updatable=false) private UUID uploadedBy;
    @Column(name="uploaded_at",nullable=false,updatable=false) private OffsetDateTime uploadedAt;
}
