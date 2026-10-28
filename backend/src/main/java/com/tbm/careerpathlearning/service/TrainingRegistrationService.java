package com.tbm.careerpathlearning.service;

import com.tbm.careerpathlearning.dto.TrainingPersonDto;
import com.tbm.careerpathlearning.dto.TrainingRegistrationFormDto;
import com.tbm.careerpathlearning.dto.TrainingRegistrationRequest;
import com.tbm.careerpathlearning.model.TrainingProgram;

import java.util.List;
import java.util.UUID;

/**
 * Registering for training sessions (FR-13), invitations to mandatory ones (FR-12-14) and the waitlist's seat offers
 * (FR-13-07). A staff member only ever acts for themselves: who they are comes from the sign-in. Every date and time
 * is Malaysia time.
 */
public interface TrainingRegistrationService {

    /** The HRDC registration form, filled in from the viewer's staff record where it can be (FR-13-03). */
    TrainingRegistrationFormDto form(Long sessionId, TrainingViewer viewer);

    /**
     * Registers the viewer, or puts them on the waitlist when no seat is free or others are already waiting
     * (FR-13-01, FR-13-02). Accepting an invitation is registering. The request carries the HRDC details when the
     * session asks for them.
     */
    void register(Long sessionId, TrainingRegistrationRequest request, TrainingViewer viewer);

    /** Cancels the viewer's own registration or waitlist place before the session starts (FR-13-06). */
    void cancelMine(Long sessionId, TrainingViewer viewer);

    void acceptOffer(Long sessionId, TrainingViewer viewer);

    void declineOffer(Long sessionId, TrainingViewer viewer);

    void declineInvitation(Long sessionId, String reason, TrainingViewer viewer);

    /** Everyone on the session or invited to it, for its trainer, HR and the Super Admin. */
    List<TrainingPersonDto> people(Long sessionId, TrainingViewer viewer);

    /** The trainer or HR registers someone: the only way in when staff can't register themselves (FR-12-04). */
    void add(Long sessionId, UUID staffId, TrainingViewer viewer);

    void remove(Long sessionId, Long registrationId, TrainingViewer viewer);

    /**
     * Offers a free seat to someone on the waitlist by hand. Within 24 hours of the start, or after the cut-off,
     * nobody is offered one automatically and the trainer fills seats this way (FR-13-07).
     */
    void offerSeat(Long sessionId, Long registrationId, TrainingViewer viewer);

    /**
     * Invites everyone in a mandatory session's audience who isn't invited or registered yet (FR-12-14). Called when
     * a session is scheduled and when it is changed, so people added to the audience are invited (decision E).
     *
     * @return how many were invited
     */
    int inviteAudience(TrainingProgram session, UUID inviterId);

    /**
     * Offers the session's free seats to the waitlist in turn, when it still allows automatic offers (FR-13-07).
     * The caller holds the session's lock.
     *
     * @return how many seats were offered
     */
    int offerFreeSeats(TrainingProgram session);

    /**
     * Ends the seat offers that ran out, telling each person, and offers the seats on. Safe to run more than once.
     *
     * @return how many offers ended
     */
    int expireOffers();
}
