package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.KpiLevel;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter @Table(name="kpi_assessment_plan_version")
public class KpiAssessmentPlanVersion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="assessment_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private KpiAssessment assessment;
    @Column(nullable=false,updatable=false) private Long reviewPeriodId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=20) private KpiLevel level;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="version_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private KpiPlanVersion version;
}
