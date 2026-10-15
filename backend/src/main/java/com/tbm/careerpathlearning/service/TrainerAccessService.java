package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TrainerAccessRequest;
import com.tbm.careerpathlearning.dto.TrainerAccessStaffDto;
import com.tbm.careerpathlearning.dto.TrainerOwnedCourseDto;
import com.tbm.careerpathlearning.dto.TrainingAuditEntryDto;

import java.util.List;
import java.util.UUID;

/** Trainer access granted to individual staff by the Super Admin (FR-20-01, FR-20-07, NFR-002). */
public interface TrainerAccessService {

    /** Whether the staff member has an individual grant in force today. Checked at sign-in and token refresh. */
    boolean hasActiveGrant(UUID staffId);

    /** Every active staff member, with whether and how they have Trainer access. */
    List<TrainerAccessStaffDto> list();

    /** Grants Trainer access, or changes the end date of the grant already in force. */
    TrainerAccessStaffDto grant(UUID staffId, TrainerAccessRequest request, UUID adminId);

    /** Revokes the grant. Refused while the person owns courses they would no longer be able to manage. */
    TrainerAccessStaffDto revoke(UUID staffId, TrainerAccessRequest request, UUID adminId);

    /** Every grant, change, revocation and lapse for the person, newest first. */
    List<TrainingAuditEntryDto> history(UUID staffId);

    List<TrainerOwnedCourseDto> ownedCourses(UUID staffId);

    /**
     * Records the lapse of every grant whose last day has passed, and tells the Super Admin about anyone who
     * still owns courses but no longer has Trainer access (FR-20-07). Safe to run more than once.
     *
     * @return how many grants lapsed
     */
    int lapseExpiredGrants();
}
