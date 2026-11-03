package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** Why the person declines the invitation to a mandatory session (FR-12-14). */
@Data
public class TrainingDeclineRequest {
    private String reason;
}
