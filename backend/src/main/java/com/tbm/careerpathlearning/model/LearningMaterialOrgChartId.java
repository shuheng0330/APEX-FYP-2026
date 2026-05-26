package com.tbm.careerpathlearning.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class LearningMaterialOrgChartId implements Serializable {

    private Long materialId;

    private Long orgChartId;
}
