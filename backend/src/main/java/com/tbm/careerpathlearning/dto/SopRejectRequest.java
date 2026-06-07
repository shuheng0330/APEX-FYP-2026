package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** Module-level reason that guides AI regeneration (FR-09-05 / FR-10-03). */
@Data
public class SopRejectRequest {
    private String reason;
}
