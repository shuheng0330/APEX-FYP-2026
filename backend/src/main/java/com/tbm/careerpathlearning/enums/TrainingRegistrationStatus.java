package com.tbm.careerpathlearning.enums;

/**
 * A staff member's place on a session (NFR-010). An Offered seat is held for the first person on the waitlist for
 * 24 hours (FR-13-07).
 */
public enum TrainingRegistrationStatus {
    REGISTERED,
    WAITLISTED,
    OFFERED,
    CANCELLED;

    /** Whether the person still has, or is waiting for, a place, and so hears about changes to the session. */
    public boolean isActive() {
        return this != CANCELLED;
    }

    /** Whether it takes up a seat: an offered seat is held until the offer is taken up or runs out. */
    public boolean holdsSeat() {
        return this == REGISTERED || this == OFFERED;
    }
}
