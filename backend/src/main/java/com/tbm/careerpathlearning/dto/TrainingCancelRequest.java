package com.tbm.careerpathlearning.dto;

import lombok.Data;

@Data
public class TrainingCancelRequest {
    /** Included in the notice to everyone registered or invited (FR-12-06). */
    private String reason;
}
