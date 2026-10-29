package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.CourseOwnerDto;
import com.tbm.careerpathlearning.dto.CourseTransferRequest;
import com.tbm.careerpathlearning.dto.TrainerAccessRequest;
import com.tbm.careerpathlearning.dto.TrainerAccessStaffDto;
import com.tbm.careerpathlearning.dto.TrainerOwnedCourseDto;
import com.tbm.careerpathlearning.dto.TrainingAuditEntryDto;
import com.tbm.careerpathlearning.model.Staff;

import java.util.List;
import java.util.UUID;

/** Trainer access granted to individual staff by the Super Admin (FR-20-01, FR-20-07, NFR-002). */
public interface TrainerAccessService {

    /** Whether the staff member has an individual grant in force today. Checked at sign-in and token refresh. */
    boolean hasActiveGrant(UUID staffId);

    /**
     * Given at sign-in to a manager, someone with direct reports: they see their team's training on the Training
     * Scoreboard (FR-10-10). It is worked out from the reporting lines, so it is not a permission a role is given.
     */
    String TEAM_TRAINING_AUTHORITY = "CAN_VIEW_TEAM_TRAINING";

    /** Whether any active staff member reports directly to this one (FR-10-10, decision C: direct reports only). */
    boolean hasTeam(UUID staffId);

    /**
     * Active staff who can run or present a training session: Trainer access through their job role or a grant in
     * force today, or HR (FR-12-05, FR-15-05). Sorted by name.
     */
    List<Staff> trainersAndHr();

    /** Active staff whose job role has the Super Admin's permission (CAN_MANAGE_ACCESS_CONTROL). */
    List<Staff> superAdmins();

    /** Every active staff member, with whether and how they have Trainer access. */
    List<TrainerAccessStaffDto> list();

    /** Grants Trainer access, or changes the end date of the grant already in force. */
    TrainerAccessStaffDto grant(UUID staffId, TrainerAccessRequest request, UUID adminId);

    /**
     * Revokes the grant. If the person would lose Trainer access while owning courses, the request must name who
     * takes them over: the courses move and the grant is revoked together (FR-20-07).
     */
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

    /** Every course with its owner, and whether the owner account is abandoned (FR-20-06). */
    List<CourseOwnerDto> courseOwners();

    /**
     * Gives a course whose owner account is abandoned to an active staff member with Trainer access, and tells
     * them. A course whose owner still has Trainer access cannot be transferred.
     */
    CourseOwnerDto transferCourse(Long courseId, CourseTransferRequest request, UUID adminId);

    /** The course's transfers, newest first. */
    List<TrainingAuditEntryDto> courseHistory(Long courseId);
}
