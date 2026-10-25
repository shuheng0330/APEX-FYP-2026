package com.tbm.careerpathlearning.enums;

/**
 * Whether a course's owner can still look after it (FR-20-06). Anything but OK means the owner account is
 * abandoned and the Super Admin may give the course to someone else.
 */
public enum CourseOwnerStatus {
    /** The owner is an active staff member with Trainer access. */
    OK,
    /** The owner is still on staff but no longer has Trainer access. */
    NO_TRAINER_ACCESS,
    /** The owner's account is inactive or deleted. */
    OWNER_LEFT,
    /** The course never had an owner, such as the early test documents. */
    NO_OWNER;

    public boolean needsNewOwner() {
        return this != OK;
    }
}
