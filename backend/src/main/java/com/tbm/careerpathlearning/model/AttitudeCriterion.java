package com.tbm.careerpathlearning.model;

import com.tbm.careerpathlearning.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity @Table(name="attitude_criterion")
public class AttitudeCriterion {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="configuration_id",nullable=false)
    private AttitudeConfiguration configuration;
    @Column(length=255) private String name;
    @Column(columnDefinition="TEXT") private String description;
    @Enumerated(EnumType.STRING) @Column(name="criterion_type",nullable=false,length=30)
    private AttitudeCriterionType criterionType;
    @Enumerated(EnumType.STRING) @Column(name="evaluation_format",length=20)
    private AttitudeEvaluationFormat evaluationFormat;
    @Column(nullable=false) private boolean active=true;
    @Column(name="display_order",nullable=false) private int displayOrder;
}
