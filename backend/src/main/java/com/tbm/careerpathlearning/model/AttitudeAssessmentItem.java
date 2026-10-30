package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @Entity
@Table(name="attitude_assessment_item",uniqueConstraints=@UniqueConstraint(name="uq_attitude_assessment_criterion",columnNames={"assessment_id","criterion_id"}))
public class AttitudeAssessmentItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="assessment_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private AttitudeAssessment assessment;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="criterion_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private AttitudeCriterion criterion;
    @Column(name="configuration_id",nullable=false,updatable=false) private Long configurationId;
    @Column(name="self_point") private Integer selfPoint;
    @Column(name="self_comment",columnDefinition="TEXT") private String selfComment;
    @Column(name="superior_point") private Integer superiorPoint;
    @Column(name="superior_comment",columnDefinition="TEXT") private String superiorComment;
}
