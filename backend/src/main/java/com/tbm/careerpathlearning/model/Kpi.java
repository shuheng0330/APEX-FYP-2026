package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.*;

@Entity @Getter @Setter
@Table(name="kpi", uniqueConstraints=@UniqueConstraint(name="uq_kpi_id_period",columnNames={"id","review_period_id"}))
public class Kpi {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="review_period_id", nullable=false, updatable=false) private Long reviewPeriodId;
    @Column(name="plan_id", nullable=false, updatable=false) private Long planId;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="plan_id",insertable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private KpiPlan plan;
    @Column(length=255) private String name;
    @Column(columnDefinition="text") private String description;
    @Column(length=255) private String perspective;
    @Column(length=255) private String kra;
    @Column(columnDefinition="text") private String target;
    @Column(name="measurement_unit",length=100) private String measurementUnit;
    @Column(precision=5,scale=2) private BigDecimal weightage;
    @ElementCollection
    @CollectionTable(name="kpi_scoring_definition",joinColumns=@JoinColumn(name="kpi_id"))
    @MapKeyColumn(name="point") @Column(name="definition",nullable=false,columnDefinition="text")
    private Map<Integer,String> scoringDefinitions = new TreeMap<>();
}
