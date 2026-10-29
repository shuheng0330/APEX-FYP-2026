package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.UUID;

/** Someone who attended without registering (decision A, 28 Oct 2026); the reason only when HR adds them late. */
@Data
public class TrainingWalkInRequest {
    private UUID staffId;
    private String reason;
}
