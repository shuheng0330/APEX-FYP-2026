package com.tbm.careerpathlearning.enums;

/**
 * Lifecycle of AI generation for an uploaded SOP document (FR-08-05).
 */
public enum SopGenerationStatus {
    PENDING,
    PARSING,
    GENERATING,
    COMPLETED,
    FAILED
}
