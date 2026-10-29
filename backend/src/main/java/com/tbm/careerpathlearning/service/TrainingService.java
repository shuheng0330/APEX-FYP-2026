package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TrainingAudienceCheckDto;
import com.tbm.careerpathlearning.dto.TrainingSessionDto;
import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.dto.TrainingTrainerDto;

import java.time.LocalDate;
import java.util.List;

/** Training sessions on the Training Calendar (FR-12). Every date and time is Malaysia time. */
public interface TrainingService {

    /**
     * The sessions between two dates that the viewer can see, in the order they run, each with its seats booked and
     * left (FR-12-11, FR-13-04). Archived sessions are listed only when asked for.
     */
    List<TrainingSessionDto> calendar(LocalDate from, LocalDate to, boolean archived, TrainingViewer viewer);

    TrainingSessionDto get(Long id, TrainingViewer viewer);

    /**
     * The viewer's upcoming sessions: ones they are registered for, waiting for, offered a seat on, or invited to and
     * yet to answer, for the My Learning home (FR-13-05).
     */
    List<TrainingSessionDto> mine(TrainingViewer viewer);

    /**
     * Sessions that have started, with someone registered, whose attendance the viewer records and hasn't been
     * submitted yet, oldest first (FR-14-10).
     */
    List<TrainingSessionDto> awaitingAttendance(TrainingViewer viewer);

    /** Schedules a session in the viewer's name (FR-12-01 to FR-12-05). */
    TrainingSessionDto schedule(TrainingSessionRequest request, TrainingViewer viewer);

    /**
     * Changes a session. Once it has started, only its title, description, trainer and training type can change, and
     * the training type only until attendance is recorded (FR-17-01). Everyone registered or invited is told when its
     * date, time, venue or delivery changes.
     */
    TrainingSessionDto update(Long id, TrainingSessionRequest request, TrainingViewer viewer);

    /** Deletes a session nobody has registered for or been invited to (FR-12-06). */
    void delete(Long id, TrainingViewer viewer);

    /** Cancels a session before it starts, telling everyone registered or invited why (FR-12-06). */
    TrainingSessionDto cancel(Long id, String reason, TrainingViewer viewer);

    /** Archives a session that has ended or was cancelled: its records are kept, but it leaves the calendar (FR-12-06). */
    TrainingSessionDto archive(Long id, TrainingViewer viewer);

    /** Puts an archived session back on the calendar, as Scheduled or Cancelled as it was. */
    TrainingSessionDto restore(Long id, TrainingViewer viewer);

    /** How many staff a session's audience covers, and a warning when a mandatory session can't seat them (FR-12-15). */
    TrainingAudienceCheckDto checkAudience(TrainingSessionRequest request);

    /** Who can run or present a session: staff with Trainer access, and HR (FR-12-05, FR-15-05). */
    List<TrainingTrainerDto> trainers();
}
