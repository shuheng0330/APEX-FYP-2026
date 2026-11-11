package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter @NoArgsConstructor @Entity
@Table(name="attitude_configuration_assignment_activity")
public class AttitudeConfigurationAssignmentActivity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="request_id",nullable=false,updatable=false,unique=true) private UUID requestId;
    @Column(name="review_period_id",nullable=false,updatable=false) private Long reviewPeriodId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="previous_configuration_id",updatable=false)
    private AttitudeConfiguration previousConfiguration;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="configuration_id",updatable=false)
    private AttitudeConfiguration configuration;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id",updatable=false) private Staff actor;
    @Column(name="recorded_at",nullable=false,updatable=false) private OffsetDateTime recordedAt;
    @Column(nullable=false,updatable=false,length=1000) private String reason;

    public AttitudeConfigurationAssignmentActivity(UUID requestId,Long reviewPeriodId,
            AttitudeConfiguration previous,AttitudeConfiguration configuration,Staff actor,OffsetDateTime at,String reason) {
        this.requestId=requestId;this.reviewPeriodId=reviewPeriodId;this.previousConfiguration=previous;
        this.configuration=configuration;this.actor=actor;this.recordedAt=at;this.reason=reason;
    }
}
