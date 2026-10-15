package com.tbm.careerpathlearning.service;

import java.util.Objects;
import java.util.UUID;

/**
 * Who is working with SOP courses.
 *
 * @param userId         the signed-in staff member
 * @param seesAllCourses whether their role may look at every course read-only: HR (CAN_ASSIGN_TRAINING) and the
 *                       Super Admin (CAN_MANAGE_ACCESS_CONTROL), FR-07-19
 */
public record SopViewer(UUID userId, boolean seesAllCourses) {

    public SopViewer {
        Objects.requireNonNull(userId, "userId");
    }
}
