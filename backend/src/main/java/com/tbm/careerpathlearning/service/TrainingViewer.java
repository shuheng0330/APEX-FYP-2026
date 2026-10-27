package com.tbm.careerpathlearning.service;

import java.util.UUID;

/**
 * The signed-in person using the Training Calendar. Staff with Trainer access (CAN_MANAGE_TRAINING) and HR
 * (CAN_ASSIGN_TRAINING) schedule sessions (FR-12-05): a trainer manages the sessions they scheduled, HR manages any.
 * They and the Super Admin (CAN_MANAGE_ACCESS_CONTROL) see every session; other staff see the sessions meant for them.
 */
public record TrainingViewer(UUID userId, boolean trainer, boolean hr, boolean superAdmin) {

    public boolean seesAllSessions() {
        return trainer || hr || superAdmin;
    }

    public boolean canSchedule() {
        return trainer || hr;
    }
}
