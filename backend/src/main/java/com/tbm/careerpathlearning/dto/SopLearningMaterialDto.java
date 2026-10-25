package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** A module's approved material for the learner. */
@Data
public class SopLearningMaterialDto {
    private Long moduleId;
    private int moduleOrder;
    private String title;
    /** The module's rich content JSON, as stored by the review page. */
    private String content;
}
