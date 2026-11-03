package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Getter @Setter @Table(name="kpi_central_result_evidence")
public class KpiCentralResultEvidence {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long itemId;
    @Column(nullable=false,updatable=false,length=100) private String storageKey;
    @Column(nullable=false,updatable=false,length=255) private String originalFilename;
    @Column(nullable=false,updatable=false,length=100) private String contentType;
    @Column(nullable=false,updatable=false) private Long sizeBytes;
    @Column(nullable=false,updatable=false) private UUID uploadedBy;
    @Column(nullable=false,updatable=false) private OffsetDateTime uploadedAt;
}
