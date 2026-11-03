package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.TrainingInvitationStatus;
import com.tbm.careerpathlearning.enums.TrainingRegistrationStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.model.TrainingInvitation;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.model.TrainingRegistration;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Who may register for a session and where they land (FR-13-01, FR-13-02, FR-12-04, FR-12-12, FR-15-07, decision B),
 * shared by registering itself and the Register button on the calendar, so the two always agree.
 */
final class TrainingRegistrationRules {

    /** How long a freed seat is held for the person it is offered to (FR-13-07). */
    static final Duration OFFER_HOLD = Duration.ofHours(24);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    /** The waitlist in the order it is served: whoever joined first (FR-13-07). */
    static final Comparator<TrainingRegistration> QUEUE = Comparator
            .comparing(TrainingRegistration::getRegisteredAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TrainingRegistration::getRegistrationId, Comparator.nullsLast(Comparator.naturalOrder()));

    private TrainingRegistrationRules() {
    }

    /** The seats a session has free, and how many people are waiting for one. */
    record Seats(int free, List<TrainingRegistration> waiting) {
        static Seats of(TrainingProgram s, Collection<TrainingRegistration> registrations) {
            int taken = (int) registrations.stream().filter(r -> r.getStatus().holdsSeat()).count();
            List<TrainingRegistration> waiting = registrations.stream()
                    .filter(r -> r.getStatus() == TrainingRegistrationStatus.WAITLISTED).sorted(QUEUE).toList();
            return new Seats(s.getCapacity() - taken, waiting);
        }
    }

    /**
     * Why the staff member can't register themselves now, or null when they can. A person already on the session
     * gets the reason too, though the calendar shows their own status instead.
     */
    static String blocker(TrainingProgram s, UUID staffId, boolean inAudience, TrainingRegistration mine,
                          TrainingInvitation invitation, TrainingProgram clash, Seats seats, LocalDateTime now) {
        if (s.getStatus() == TrainingSessionStatus.CANCELLED) return "This session was cancelled.";
        if (s.getStatus() == TrainingSessionStatus.ARCHIVED) return "This session is archived.";
        if (!now.isBefore(s.startsAt())) return "This session has started, so registration is closed.";
        if (staffId.equals(s.getTrainerId())) {
            return s.getKind() == TrainingSessionKind.STAFF_SHARING
                    ? "You present this session, so you can't register for it." // FR-15-07
                    : "You run this session, so you can't register for it.";
        }
        if (mine != null && mine.getStatus().isActive()) {
            return switch (mine.getStatus()) {
                case REGISTERED -> "You're registered for this session.";
                case WAITLISTED -> "You're on the waitlist for this session.";
                default -> "A seat is held for you: accept it instead.";
            };
        }
        boolean invited = invitation != null && invitation.getStatus() == TrainingInvitationStatus.PENDING;
        if (s.getRegistrationCutoff() != null && now.toLocalDate().isAfter(s.getRegistrationCutoff())) {
            return "Registration closed at the end of " + s.getRegistrationCutoff().format(DAY) + ".";
        }
        if (!s.isSelfRegistration() && !invited) return "The trainer or HR registers people for this session.";
        if (!inAudience && !invited) return "This session isn't open to you.";
        if (!s.isWaitlistEnabled() && seats.free() <= 0) return "This session is full.";
        if (clash != null) {
            return "It clashes with \"" + clash.getTitle() + "\", " + clash.getStartTime().format(TIME) + " to "
                    + clash.getEndTime().format(TIME) + ", which you're registered for.";
        }
        return null;
    }

    /**
     * Whether registering now puts the person on the waitlist: when no seat is free, or when others are already
     * waiting, so nobody jumps the queue (FR-13-02).
     */
    static boolean joinsWaitlist(TrainingProgram s, Seats seats) {
        return s.isWaitlistEnabled() && (seats.free() <= 0 || !seats.waiting().isEmpty());
    }

    /**
     * Whether freed seats are offered to the waitlist automatically: not within 24 hours of the start, nor after the
     * registration cut-off, when the trainer fills them instead (FR-13-07).
     */
    static boolean offersAutomatically(TrainingProgram s, LocalDateTime now) {
        return s.getStatus() == TrainingSessionStatus.SCHEDULED
                && now.isBefore(s.startsAt().minus(OFFER_HOLD))
                && (s.getRegistrationCutoff() == null || !now.toLocalDate().isAfter(s.getRegistrationCutoff()));
    }

    /** Another scheduled session the person has a seat on that overlaps this one in time (decision B), if any. */
    static TrainingProgram clash(TrainingProgram s, Collection<TrainingProgram> seatsElsewhere) {
        return seatsElsewhere.stream()
                .filter(o -> !Objects.equals(o.getTrainingId(), s.getTrainingId()))
                .filter(o -> o.getStatus() == TrainingSessionStatus.SCHEDULED && !o.isDeleted())
                .filter(o -> o.getSessionDate().equals(s.getSessionDate()))
                .filter(o -> o.getStartTime().isBefore(s.getEndTime()) && s.getStartTime().isBefore(o.getEndTime()))
                .findFirst().orElse(null);
    }
}
