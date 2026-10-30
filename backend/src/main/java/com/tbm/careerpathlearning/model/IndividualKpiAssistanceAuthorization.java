package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.KpiAssistanceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity @Getter @Setter
@Table(name="individual_kpi_assistance_authorization")
public class IndividualKpiAssistanceAuthorization {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="owner_participant_id",nullable=false,updatable=false)
    private ReviewPeriodParticipant ownerParticipant;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="superior_id",nullable=false,updatable=false) private Staff superior;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20)
    private KpiAssistanceStatus status=KpiAssistanceStatus.REQUESTED;
    @Column(nullable=false,updatable=false) private OffsetDateTime requestedAt;
    @Column(columnDefinition="TEXT",updatable=false) private String requestReason;
    private OffsetDateTime authorizedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="authorized_by") private Staff authorizedBy;
    private OffsetDateTime consumedAt;
    private OffsetDateTime rejectedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="rejected_by") private Staff rejectedBy;
    @Column(columnDefinition="TEXT") private String rejectionReason;
}
