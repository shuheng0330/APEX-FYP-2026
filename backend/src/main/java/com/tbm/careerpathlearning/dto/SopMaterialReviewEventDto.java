package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/** One entry in a module's material review history (FR-07-14). */
@Data
public class SopMaterialReviewEventDto {
    private Long id;
    /** A MaterialReviewAction name. */
    private String action;
    private String detail;
    /** Name of the staff member who acted; null when the AI did. */
    private String actorName;
    private OffsetDateTime createdAt;
}
