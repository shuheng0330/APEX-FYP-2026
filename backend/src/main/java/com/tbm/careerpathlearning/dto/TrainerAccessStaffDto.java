package com.tbm.careerpathlearning.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** A staff member as shown on the Super Admin's Trainer Access page (FR-20-01). */
@Data
public class TrainerAccessStaffDto {
    private UUID staffId;
    private String name;
    private String email;
    private String department;
    private String jobRole;
    /** Has Trainer access through their job role's permissions. */
    private boolean viaRole;
    /** Has an individual grant in force today. */
    private boolean viaGrant;
    /** The grant's last day, or null when it has no end date; only set while the grant is in force. */
    private LocalDate grantEndDate;
    private String grantedByName;
    private OffsetDateTime grantedAt;
    /** Courses this person owns, which must be transferred before they lose Trainer access (FR-20-07). */
    private int ownedCourses;
}
