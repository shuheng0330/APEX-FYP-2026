package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;

@Entity @Getter @Setter
@Table(name="employee_kpi_assignment",uniqueConstraints=@UniqueConstraint(name="uq_employee_kpi_assignment",columnNames={"kpi_id","participant_id"}))
public class EmployeeKpiAssignment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="review_period_id",nullable=false,updatable=false) private Long reviewPeriodId;
    @Column(name="kpi_id",nullable=false,updatable=false) private Long kpiId;
    @Column(name="participant_id",nullable=false,updatable=false) private Long participantId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="kpi_id",insertable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Kpi kpi;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="participant_id",insertable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private ReviewPeriodParticipant participant;
    @Column(name="assigned_at",nullable=false,updatable=false) private OffsetDateTime assignedAt;
}
