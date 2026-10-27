package com.tbm.careerpathlearning.enums;

/**
 * Where a training session is in its life (FR-12-06, NFR-010). Whether it is still to come, under way or over is
 * worked out from its date and times on the Malaysia clock ({@link TrainingSessionTiming}), so it is never out of date.
 */
public enum TrainingSessionStatus {
    SCHEDULED,
    CANCELLED,
    ARCHIVED
}
