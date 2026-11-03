package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Getter @Setter @Table(name="kpi_central_result_item")
public class KpiCentralResultItem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="result_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private KpiCentralResult result;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="kpi_id",nullable=false,updatable=false,foreignKey=@ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Kpi kpi;
    @Column(nullable=false,updatable=false) private Long planVersionId;
    @Column(columnDefinition="text") private String actualAchievement;
    private Integer point;
    @Column(columnDefinition="text") private String comment;
}
