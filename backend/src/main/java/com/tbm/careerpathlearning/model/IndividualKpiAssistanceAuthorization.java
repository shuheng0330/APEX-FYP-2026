package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.KpiAssistanceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity @Getter @Setter
@Table(name="individual_kpi_assistance_authorization",
    uniqueConstraints=@UniqueConstraint(name="uq_assistance_case",columnNames={"owner_participant_id","superior_id"}))
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
    private OffsetDateTime authorizedAt;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="authorized_by") private Staff authorizedBy;
    private OffsetDateTime consumedAt;
}
