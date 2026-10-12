package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** Course feedback from a learner who completed it (FR-11-06). */
@Data
public class SopFeedbackRequest {
    /** 1 to 5. */
    private Integer rating;
    private String comment;
}
