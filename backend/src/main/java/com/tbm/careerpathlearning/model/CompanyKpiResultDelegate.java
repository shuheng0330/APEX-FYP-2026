package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Getter @Setter @Table(name="company_kpi_result_delegate")
public class CompanyKpiResultDelegate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,updatable=false) private Long reviewPeriodId;
    @Column(nullable=false,updatable=false) private UUID staffId;
    @Column(nullable=false,updatable=false) private UUID assignedBy;
    @Column(nullable=false,updatable=false) private OffsetDateTime assignedAt;
    private UUID revokedBy;
    private OffsetDateTime revokedAt;
}
