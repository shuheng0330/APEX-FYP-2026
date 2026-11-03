package com.tbm.careerpathlearning.enums;

/**
 * The training type of a Physical or Hybrid training session (FR-17-01). Product Training and Skill-Based Training
 * count towards attendees' training KPI; Other does not, though its hours are still recorded (FR-17-05).
 */
public enum TrainingType {
    PRODUCT,
    SKILL_BASED,
    OTHER
}
