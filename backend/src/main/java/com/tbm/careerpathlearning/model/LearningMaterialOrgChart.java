package com.tbm.careerpathlearning.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "learning_material_org_chart")
@SQLRestriction("org_chart_id IN (SELECT oc.id FROM org_chart oc WHERE oc.is_deleted = false)")
public class LearningMaterialOrgChart {

    @EmbeddedId
    private LearningMaterialOrgChartId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("materialId")
    @JoinColumn(name = "material_id")
    private LearningMaterial learningMaterial;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("orgChartId")
    @JoinColumn(name = "org_chart_id")
    private OrgChart orgChart;
}
