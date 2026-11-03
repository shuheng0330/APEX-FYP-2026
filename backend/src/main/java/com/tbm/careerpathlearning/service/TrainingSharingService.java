package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TrainingSessionDto;
import com.tbm.careerpathlearning.dto.TrainingSharingBoardDto;

/** Staff sharing sessions (FR-15): the Staff Sharing page, and asking to present one after a Product Training. */
public interface TrainingSharingService {

    /** A year's sharing sessions, presenters and reach, for all staff; this year when none is given. */
    TrainingSharingBoardDto board(Integer year, TrainingViewer viewer);

    /**
     * Asks every Super Admin for Trainer access, to present a staff sharing session, after attending a Product Training
     * session whose attendance is submitted (FR-15-06). Once per person per session; logged.
     */
    TrainingSessionDto requestTrainerAccess(Long sessionId, TrainingViewer viewer);
}
