package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class TrainingAuditEntryDto {
    private Long id;
    private String action;
    /** Name of the staff member who acted; null when a scheduled job did. */
    private String actorName;
    private String reason;
    private String detail;
    private OffsetDateTime createdAt;
}
