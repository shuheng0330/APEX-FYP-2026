package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

/** Granting, changing or revoking Trainer access; every change needs a reason (FR-20-01). */
@Data
public class TrainerAccessRequest {
    /** The last day the access works; null for no end date. Ignored when revoking. */
    private LocalDate endDate;
    private String reason;
    /**
     * Revoking only: who takes over the person's courses when they would lose Trainer access while owning some
     * (FR-20-07). The courses move and the access is revoked together, or neither happens.
     */
    private UUID transferToId;
}
