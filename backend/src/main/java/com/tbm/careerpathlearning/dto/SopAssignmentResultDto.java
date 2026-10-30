package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** What an "Assign" did: newly enrolled, terms updated, and already-completed staff left alone. */
@Data
public class SopAssignmentResultDto {
    private SopAssignmentDto assignment;
    private int assigned;
    private int updated;
    private int skippedCompleted;
}
