package com.tbm.careerpathlearning.dto;

import lombok.Data;

/** Publishing a draft version (FR-07-18): whether staff who completed an earlier version must complete this one. */
@Data
public class SopPublishRequest {
    private boolean redoCompleted;
}
