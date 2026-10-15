package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDate;

/** Granting, changing or revoking Trainer access; every change needs a reason (FR-20-01). */
@Data
public class TrainerAccessRequest {
    /** The last day the access works; null for no end date. Ignored when revoking. */
    private LocalDate endDate;
    private String reason;
}
