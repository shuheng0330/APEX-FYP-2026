package com.tbm.careerpathlearning.dto;

import com.tbm.careerpathlearning.enums.CourseOwnerStatus;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A course and its owner, on the Super Admin's Course owners tab (FR-20-06). */
@Data
public class CourseOwnerDto {
    private Long courseId;
    private String title;
    private String version;
    /** Null when the course has no owner. */
    private UUID ownerId;
    private String ownerName;
    private CourseOwnerStatus status;
    /** Whether the course may be transferred: only when its owner account is abandoned. */
    private boolean needsNewOwner;
    private int enrolledCount;
    private OffsetDateTime createdAt;
}
