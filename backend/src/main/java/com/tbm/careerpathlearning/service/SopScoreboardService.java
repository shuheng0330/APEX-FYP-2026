package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.SopCompetencyScoreDto;
import com.tbm.careerpathlearning.dto.SopEnrollmentProgressDto;
import com.tbm.careerpathlearning.dto.SopScoreboardDto;

import java.util.List;
import java.util.UUID;

/**
 * The Training Scoreboard (FR-10): staff progress on SOP courses, including retraining, and the
 * final competency scores the Staff Appraisal module reads.
 */
public interface SopScoreboardService {

    /**
     * Every enrolment on the courses the user may see: the courses they uploaded, or every course
     * when {@code seeAll} (CAN_ASSIGN_TRAINING).
     */
    default SopScoreboardDto scoreboard(UUID userId, boolean seeAll) {
        return scoreboard(userId, seeAll, false);
    }

    /** As above, plus, for a manager ({@code team}), every enrolment of the staff who report to them (FR-10-10). */
    SopScoreboardDto scoreboard(UUID userId, boolean seeAll, boolean team);

    /** One enrolment, module by module, with every quiz attempt (FR-10-02). */
    default SopEnrollmentProgressDto enrollment(Long enrollmentId, UUID userId, boolean seeAll) {
        return enrollment(enrollmentId, userId, seeAll, false);
    }

    /** As above; a manager may also open their direct reports' enrolments (FR-10-10). */
    SopEnrollmentProgressDto enrollment(Long enrollmentId, UUID userId, boolean seeAll, boolean team);

    /** A staff member's completed SOP courses and their final scores, newest first (FR-10-06). */
    List<SopCompetencyScoreDto> competencyScores(UUID staffId);
}
