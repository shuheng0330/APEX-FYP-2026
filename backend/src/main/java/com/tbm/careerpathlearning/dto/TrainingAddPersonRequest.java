package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.UUID;

/** The staff member the trainer or HR registers for a session. */
@Data
public class TrainingAddPersonRequest {
    private UUID staffId;
}
