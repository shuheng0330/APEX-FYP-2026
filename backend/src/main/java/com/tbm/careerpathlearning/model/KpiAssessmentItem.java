package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.*;

@Entity @Getter @Setter
@Table(name="kpi_assessment_item",uniqueConstraints=@UniqueConstraint(name="uq_assessment_assignment",columnNames={"assessment_id","assignment_id"}))
public class KpiAssessmentItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="assessment_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private KpiAssessment assessment;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="assignment_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private EmployeeKpiAssignment assignment;
    @Column(name="participant_id",nullable=false,updatable=false) private Long participantId;
    @Column(name="review_period_id",nullable=false,updatable=false) private Long reviewPeriodId;
    @Column(name="self_point") private Integer selfPoint;
    @Column(name="self_comment",columnDefinition="text") private String selfComment;
    @Column(name="superior_point") private Integer superiorPoint;
    @Column(name="superior_comment",columnDefinition="text") private String superiorComment;
    @OneToMany(mappedBy="item") @OrderBy("id ASC")
    private List<KpiAssessmentEvidence> evidence=new ArrayList<>();
}
