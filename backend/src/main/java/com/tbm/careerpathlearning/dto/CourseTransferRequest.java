package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.util.UUID;

/** Giving an abandoned course to another person with Trainer access; a reason is required (FR-20-06). */
@Data
public class CourseTransferRequest {
    private UUID newOwnerId;
    private String reason;
}
