package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrainingPersonDto;
import com.tbm.careerpathlearning.dto.TrainingRegistrationFormDto;
import com.tbm.careerpathlearning.dto.TrainingRegistrationRequest;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.enums.TrainingInvitationStatus;
import com.tbm.careerpathlearning.enums.TrainingRegistrationStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingInvitation;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.model.TrainingRegistration;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingInvitationRepository;
import com.tbm.careerpathlearning.repository.TrainingProgramRepository;
import com.tbm.careerpathlearning.repository.TrainingRegistrationRepository;
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.TrainingBranches;
import com.tbm.careerpathlearning.service.TrainingRegistrationService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import com.tbm.careerpathlearning.service.impl.TrainingRegistrationRules.Seats;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TrainingRegistrationServiceImpl implements TrainingRegistrationService {

    /** A Malaysian IC number (12 digits, dashes optional) or a passport number. */
    private static final Pattern ID_NUMBER = Pattern.compile("^(\\d{6}-?\\d{2}-?\\d{4}|[A-Z0-9]{6,20})$");
    private static final Pattern PHONE = Pattern.compile("^\\+?\\d[\\d -]{7,18}$");
    private static final int MAX_REASON = 500;
    private static final DateTimeFormatter OFFER_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    /** How people are listed for the trainer: who has a seat first, who has left last. */
    private static final List<String> PEOPLE_ORDER =
            List.of("REGISTERED", "OFFERED", "WAITLISTED", "INVITED", "DECLINED", "NO_RESPONSE", "CANCELLED");

    @Autowired private TrainingProgramRepository trainingProgramRepository;
    @Autowired private TrainingRegistrationRepository trainingRegistrationRepository;
    @Autowired private TrainingInvitationRepository trainingInvitationRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private NotificationService notificationService;
    /** Malaysia time, whatever time zone the server runs in. */
    @Autowired private Clock clock;

    @Override
    @Transactional(readOnly = true)
    public TrainingRegistrationFormDto form(Long sessionId, TrainingViewer viewer) {
        find(sessionId);
        Staff me = active(viewer.userId(), "Your account isn't active, so you can't register.");
        OrgChart department = SopAssignmentServiceImpl.department(me);
        TrainingRegistrationFormDto dto = new TrainingRegistrationFormDto();
        dto.setName(me.getName());
        dto.setEmail(me.getEmail());
        dto.setDepartmentName(department == null ? null : department.getName());
        dto.setRoleName(me.getRole() == null ? null : me.getRole().getName());
        dto.setBranches(TrainingBranches.BY_REGION);
        return dto;
    }

    @Override
    @Transactional
    public void register(Long sessionId, TrainingRegistrationRequest request, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = locked(sessionId);
        Staff me = active(viewer.userId(), "Your account isn't active, so you can't register.");
        List<TrainingRegistration> registrations = trainingRegistrationRepository.findAllByTrainingId(sessionId);
        TrainingRegistration mine = of(registrations, me.getId());
        TrainingInvitation invitation = trainingInvitationRepository.findByTrainingIdAndStaffId(sessionId, me.getId()).orElse(null);
        Seats seats = Seats.of(s, registrations);
        String blocker = TrainingRegistrationRules.blocker(s, me.getId(), TrainingServiceImpl.inAudience(s, me), mine,
                invitation, TrainingRegistrationRules.clash(s, seatsElsewhere(me.getId())), seats, now);
        if (blocker != null) {
            throw new BadRequestException(blocker);
        }
        Form form = s.isRegistrationForm() ? checkedForm(request) : null;
        boolean waitlisted = TrainingRegistrationRules.joinsWaitlist(s, seats);

        TrainingRegistration r = place(mine, s, me.getId(), waitlisted, now);
        if (form != null) {
            r.setIdNumber(form.idNumber());
            r.setPhone(form.phone());
            r.setBranch(form.branch());
            r.setPdpaConsentAt(now);
        }
        trainingRegistrationRepository.save(r);
        accept(invitation, now);
        String body = waitlisted
                ? "\"" + s.getTitle() + "\" on " + TrainingServiceImpl.when(s) + " is full, so you're number "
                + (seats.waiting().size() + 1) + " on the waitlist. If a seat frees up, it is offered to you in turn."
                : "You're registered for \"" + s.getTitle() + "\" on " + TrainingServiceImpl.when(s) + ", "
                + TrainingServiceImpl.where(s) + ".";
        tell(me.getId(), waitlisted ? NotificationType.SESSION_WAITLISTED : NotificationType.SESSION_REGISTERED,
                (waitlisted ? "On the waitlist: " : "Registered: ") + s.getTitle(), body, s);
    }

    @Override
    @Transactional
    public void cancelMine(Long sessionId, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = locked(sessionId);
        TrainingRegistration r = trainingRegistrationRepository.findByTrainingIdAndStaffId(sessionId, viewer.userId())
                .filter(x -> x.getStatus().isActive())
                .orElseThrow(() -> new BadRequestException("You aren't registered for this session."));
        if (!now.isBefore(s.startsAt())) {
            throw new BadRequestException("This session has started, so your registration can't be cancelled now.");
        }
        boolean heldSeat = r.getStatus().holdsSeat();
        end(r, now, viewer.userId(), r.getStatus() == TrainingRegistrationStatus.OFFERED
                ? "Declined the offered seat" : "Cancelled by the staff member");
        if (heldSeat) {
            offerFreeSeats(s);
        }
    }

    @Override
    @Transactional
    public void acceptOffer(Long sessionId, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = locked(sessionId);
        TrainingRegistration r = offered(sessionId, viewer.userId());
        if (!now.isBefore(r.getOfferExpiresAt())) {
            throw new BadRequestException("This offer ran out at " + r.getOfferExpiresAt().format(OFFER_TIME) + ".");
        }
        if (s.getStatus() != TrainingSessionStatus.SCHEDULED || !now.isBefore(s.startsAt())) {
            throw new BadRequestException("This session can no longer be joined.");
        }
        r.setStatus(TrainingRegistrationStatus.REGISTERED);
        r.setOfferExpiresAt(null);
        tell(viewer.userId(), NotificationType.SESSION_REGISTERED, "Registered: " + s.getTitle(),
                "You took up the seat on \"" + s.getTitle() + "\" on " + TrainingServiceImpl.when(s) + ", "
                        + TrainingServiceImpl.where(s) + ".", s);
    }

    @Override
    @Transactional
    public void declineOffer(Long sessionId, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = locked(sessionId);
        TrainingRegistration r = offered(sessionId, viewer.userId());
        end(r, now, viewer.userId(), "Declined the offered seat");
        offerFreeSeats(s);
    }

    @Override
    @Transactional
    public void declineInvitation(Long sessionId, String reason, TrainingViewer viewer) {
        find(sessionId);
        TrainingInvitation invitation = trainingInvitationRepository.findByTrainingIdAndStaffId(sessionId, viewer.userId())
                .filter(i -> i.getStatus() == TrainingInvitationStatus.PENDING)
                .orElseThrow(() -> new BadRequestException("You have no invitation to answer for this session."));
        String why = reason == null || reason.isBlank() ? null : reason.trim();
        if (why == null) {
            throw new BadRequestException("Say why you can't attend. Your trainer sees the reason.");
        }
        if (why.length() > MAX_REASON) {
            throw new BadRequestException("Keep the reason to " + MAX_REASON + " characters.");
        }
        invitation.setStatus(TrainingInvitationStatus.DECLINED);
        invitation.setReason(why);
        invitation.setRespondedAt(LocalDateTime.now(clock));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingPersonDto> people(Long sessionId, TrainingViewer viewer) {
        TrainingProgram s = find(sessionId);
        boolean details = TrainingServiceImpl.canManage(s, viewer) || viewer.userId().equals(s.getTrainerId());
        if (!details && !viewer.superAdmin()) {
            throw new ForbiddenRequestException("Only the session's trainer, HR and the Super Admin can see who is registered.");
        }
        List<TrainingRegistration> registrations = trainingRegistrationRepository.findAllByTrainingId(sessionId);
        List<TrainingInvitation> invitations = trainingInvitationRepository.findAllByTrainingId(sessionId);
        Set<UUID> ids = new HashSet<>();
        registrations.forEach(r -> {
            ids.add(r.getStaffId());
            if (r.getAddedBy() != null) ids.add(r.getAddedBy());
        });
        invitations.forEach(i -> ids.add(i.getStaffId()));
        Map<UUID, Staff> staff = ids.isEmpty() ? Map.of() : staffRepository.findAllWithRoleByIdIn(ids).stream()
                .collect(Collectors.toMap(Staff::getId, x -> x));
        List<TrainingRegistration> queue = Seats.of(s, registrations).waiting();

        List<TrainingPersonDto> rows = new ArrayList<>();
        Set<UUID> registered = new HashSet<>();
        for (TrainingRegistration r : registrations) {
            registered.add(r.getStaffId());
            TrainingPersonDto row = person(staff.get(r.getStaffId()), r.getStaffId());
            row.setStatus(r.getStatus().name());
            row.setRegistrationId(r.getRegistrationId());
            row.setRegisteredAt(r.getRegisteredAt());
            row.setOfferExpiresAt(r.getStatus() == TrainingRegistrationStatus.OFFERED ? r.getOfferExpiresAt() : null);
            row.setWaitlistPosition(r.getStatus() == TrainingRegistrationStatus.WAITLISTED ? queue.indexOf(r) + 1 : null);
            row.setNote(r.getCancelReason());
            Staff adder = r.getAddedBy() == null ? null : staff.get(r.getAddedBy());
            row.setAddedByName(r.getAddedBy() == null ? null : adder == null ? "Unknown user" : adder.getName());
            if (details) {
                row.setIdNumber(masked(r.getIdNumber()));
                row.setPhone(r.getPhone());
                row.setBranch(r.getBranch());
            }
            rows.add(row);
        }
        for (TrainingInvitation i : invitations) {
            if (registered.contains(i.getStaffId())) continue;
            TrainingPersonDto row = person(staff.get(i.getStaffId()), i.getStaffId());
            row.setStatus(switch (i.getStatus()) {
                case PENDING, ACCEPTED -> "INVITED";
                case DECLINED -> "DECLINED";
                case NO_RESPONSE -> "NO_RESPONSE";
            });
            row.setNote(i.getReason());
            rows.add(row);
        }
        rows.sort(Comparator.comparingInt((TrainingPersonDto p) -> PEOPLE_ORDER.indexOf(p.getStatus()))
                .thenComparing(p -> p.getWaitlistPosition() == null ? 0 : p.getWaitlistPosition())
                .thenComparing(p -> Objects.toString(p.getName(), ""), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    @Override
    @Transactional
    public void add(Long sessionId, UUID staffId, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = manageable(sessionId, viewer);
        open(s, now);
        Staff person = active(staffId, "Choose an active staff member.");
        if (person.getId().equals(s.getTrainerId())) {
            throw new BadRequestException(person.getName() + " runs this session, so they can't be registered for it.");
        }
        List<TrainingRegistration> registrations = trainingRegistrationRepository.findAllByTrainingId(sessionId);
        TrainingRegistration existing = of(registrations, person.getId());
        if (existing != null && existing.getStatus().isActive()) {
            throw new BadRequestException(person.getName() + " is already "
                    + (existing.getStatus() == TrainingRegistrationStatus.WAITLISTED ? "on the waitlist." : "on this session."));
        }
        TrainingProgram clash = TrainingRegistrationRules.clash(s, seatsElsewhere(person.getId()));
        if (clash != null) {
            throw new BadRequestException(person.getName() + " is registered for \"" + clash.getTitle()
                    + "\" at the same time.");
        }
        Seats seats = Seats.of(s, registrations);
        if (seats.free() <= 0 && !s.isWaitlistEnabled()) {
            throw new BadRequestException("This session is full. Add seats first.");
        }
        boolean waitlisted = seats.free() <= 0; // the trainer's own pick takes a free seat
        TrainingRegistration r = place(existing, s, person.getId(), waitlisted, now);
        r.setAddedBy(viewer.userId());
        trainingRegistrationRepository.save(r);
        accept(trainingInvitationRepository.findByTrainingIdAndStaffId(sessionId, person.getId()).orElse(null), now);
        String by = name(viewer.userId());
        tell(person.getId(), waitlisted ? NotificationType.SESSION_WAITLISTED : NotificationType.SESSION_REGISTERED,
                (waitlisted ? "On the waitlist: " : "Registered: ") + s.getTitle(),
                by + (waitlisted ? " put you on the waitlist for \"" : " registered you for \"") + s.getTitle() + "\" on "
                        + TrainingServiceImpl.when(s) + ", " + TrainingServiceImpl.where(s) + ".", s);
    }

    @Override
    @Transactional
    public void remove(Long sessionId, Long registrationId, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = manageable(sessionId, viewer);
        TrainingRegistration r = onSession(sessionId, registrationId);
        if (!r.getStatus().isActive()) {
            throw new BadRequestException("They are no longer on this session.");
        }
        if (!now.isBefore(s.startsAt())) {
            throw new BadRequestException("This session has started: record their attendance instead.");
        }
        boolean heldSeat = r.getStatus().holdsSeat();
        String by = name(viewer.userId());
        end(r, now, viewer.userId(), "Removed by " + by);
        tell(r.getStaffId(), NotificationType.SESSION_REMOVED, "Removed from: " + s.getTitle(),
                by + " took you off \"" + s.getTitle() + "\" on " + TrainingServiceImpl.when(s) + ".", s);
        if (heldSeat) {
            offerFreeSeats(s);
        }
    }

    @Override
    @Transactional
    public void offerSeat(Long sessionId, Long registrationId, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = manageable(sessionId, viewer);
        open(s, now);
        TrainingRegistration r = onSession(sessionId, registrationId);
        if (r.getStatus() != TrainingRegistrationStatus.WAITLISTED) {
            throw new BadRequestException("Only someone on the waitlist can be offered a seat.");
        }
        if (Seats.of(s, trainingRegistrationRepository.findAllByTrainingId(sessionId)).free() <= 0) {
            throw new BadRequestException("No seat is free. Add seats, or wait for someone to cancel.");
        }
        LocalDateTime hold = now.plus(TrainingRegistrationRules.OFFER_HOLD);
        offer(s, r, now, hold.isBefore(s.startsAt()) ? hold : s.startsAt());
    }

    @Override
    @Transactional
    public int inviteAudience(TrainingProgram s, UUID inviterId) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!s.isMandatory() || s.getStatus() != TrainingSessionStatus.SCHEDULED || !now.isBefore(s.startsAt())) {
            return 0;
        }
        Set<UUID> known = new HashSet<>();
        trainingInvitationRepository.findAllByTrainingId(s.getTrainingId()).forEach(i -> known.add(i.getStaffId()));
        trainingRegistrationRepository.findAllByTrainingId(s.getTrainingId()).forEach(r -> known.add(r.getStaffId()));
        String by = s.getRegistrationCutoff() == null ? "Register before it starts."
                : "Register by the end of " + s.getRegistrationCutoff().format(DAY) + ".";
        int invited = 0;
        for (Staff person : staffRepository.findAllActiveWithRole()) {
            if (known.contains(person.getId()) || person.getId().equals(s.getTrainerId())
                    || !TrainingServiceImpl.inAudience(s, person)) {
                continue;
            }
            TrainingInvitation i = new TrainingInvitation();
            i.setTrainingId(s.getTrainingId());
            i.setStaffId(person.getId());
            i.setInvitedBy(inviterId);
            i.setStatus(TrainingInvitationStatus.PENDING);
            i.setInvitedAt(now);
            trainingInvitationRepository.save(i);
            tell(person.getId(), NotificationType.SESSION_INVITATION, "Invitation: " + s.getTitle(),
                    "You're invited to the mandatory session \"" + s.getTitle() + "\" on " + TrainingServiceImpl.when(s)
                            + ", " + TrainingServiceImpl.where(s) + ". " + by, s);
            invited++;
        }
        return invited;
    }

    @Override
    @Transactional
    public int offerFreeSeats(TrainingProgram s) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (!TrainingRegistrationRules.offersAutomatically(s, now)) {
            return 0;
        }
        Seats seats = Seats.of(s, trainingRegistrationRepository.findAllByTrainingId(s.getTrainingId()));
        int offered = 0;
        for (TrainingRegistration r : seats.waiting()) {
            if (offered >= seats.free()) break;
            offer(s, r, now, now.plus(TrainingRegistrationRules.OFFER_HOLD));
            offered++;
        }
        return offered;
    }

    @Override
    @Transactional
    public int expireOffers() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, List<TrainingRegistration>> bySession = trainingRegistrationRepository
                .findAllByStatusAndOfferExpiresAtLessThanEqual(TrainingRegistrationStatus.OFFERED, now).stream()
                .collect(Collectors.groupingBy(TrainingRegistration::getTrainingId, LinkedHashMap::new, Collectors.toList()));
        int ended = 0;
        for (Map.Entry<Long, List<TrainingRegistration>> e : bySession.entrySet()) {
            TrainingProgram s = trainingProgramRepository.lockById(e.getKey()).orElse(null);
            boolean live = s != null && s.getStatus() == TrainingSessionStatus.SCHEDULED && now.isBefore(s.startsAt());
            for (TrainingRegistration r : e.getValue()) {
                LocalDateTime until = r.getOfferExpiresAt();
                end(r, now, null, "Did not take up the offered seat in time");
                ended++;
                if (live) {
                    tell(r.getStaffId(), NotificationType.SESSION_OFFER_EXPIRED, "Seat offer ended: " + s.getTitle(),
                            "The seat on \"" + s.getTitle() + "\" on " + TrainingServiceImpl.when(s) + " was held for you until "
                                    + until.format(OFFER_TIME) + ", so it has passed to the next person and you've left "
                                    + "the waitlist. You can register again while registration is open.", s);
                }
            }
            if (live) {
                offerFreeSeats(s);
            }
        }
        return ended;
    }

    // --- helpers -----------------------------------------------------------------------

    /** The HRDC details, checked and tidied (FR-13-03). */
    private record Form(String idNumber, String phone, String branch) {
    }

    private static Form checkedForm(TrainingRegistrationRequest r) {
        String id = r == null || r.getIdNumber() == null ? "" : r.getIdNumber().replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (!ID_NUMBER.matcher(id).matches()) {
            throw new BadRequestException("Enter your IC number (12 digits) or passport number.");
        }
        String phone = r.getPhone() == null ? "" : r.getPhone().trim();
        if (!PHONE.matcher(phone).matches()) {
            throw new BadRequestException("Enter a phone number, for example 012-345 6789.");
        }
        if (!TrainingBranches.isBranch(r.getBranch())) {
            throw new BadRequestException("Choose your branch.");
        }
        if (!r.isPdpaConsent()) {
            throw new BadRequestException("Tick the box to agree to the PDPA notice before registering.");
        }
        return new Form(id, phone, r.getBranch());
    }

    /** The person's registration, new or reused, as registered or waitlisted from now. */
    private static TrainingRegistration place(TrainingRegistration existing, TrainingProgram s, UUID staffId,
                                              boolean waitlisted, LocalDateTime now) {
        TrainingRegistration r = existing != null ? existing : new TrainingRegistration();
        r.setTrainingId(s.getTrainingId());
        r.setStaffId(staffId);
        r.setStatus(waitlisted ? TrainingRegistrationStatus.WAITLISTED : TrainingRegistrationStatus.REGISTERED);
        r.setRegisteredAt(now);
        r.setAddedBy(null);
        r.setOfferedAt(null);
        r.setOfferExpiresAt(null);
        r.setCancelledAt(null);
        r.setCancelledBy(null);
        r.setCancelReason(null);
        r.eraseForm();
        return r;
    }

    /** Ends a registration; its HRDC details go with it (decision C). */
    private static void end(TrainingRegistration r, LocalDateTime now, UUID by, String reason) {
        r.setStatus(TrainingRegistrationStatus.CANCELLED);
        r.setCancelledAt(now);
        r.setCancelledBy(by);
        r.setCancelReason(reason);
        r.setOfferExpiresAt(null);
        r.eraseForm();
    }

    private void offer(TrainingProgram s, TrainingRegistration r, LocalDateTime now, LocalDateTime until) {
        r.setStatus(TrainingRegistrationStatus.OFFERED);
        r.setOfferedAt(now);
        r.setOfferExpiresAt(until);
        tell(r.getStaffId(), NotificationType.SESSION_SEAT_OFFERED, "Seat offered: " + s.getTitle(),
                "A seat on \"" + s.getTitle() + "\" on " + TrainingServiceImpl.when(s) + " is free for you. Accept it in the "
                        + "Training Calendar by " + until.format(OFFER_TIME) + ", or it passes to the next person.", s);
    }

    private static void accept(TrainingInvitation invitation, LocalDateTime now) {
        if (invitation != null && invitation.getStatus() == TrainingInvitationStatus.PENDING) {
            invitation.setStatus(TrainingInvitationStatus.ACCEPTED);
            invitation.setRespondedAt(now);
        }
    }

    private void tell(UUID staffId, NotificationType type, String title, String body, TrainingProgram s) {
        notificationService.notify(staffId, type, title, body, TrainingServiceImpl.link(s));
    }

    /** Sessions the person has a seat on, to check for clashes (decision B). */
    private List<TrainingProgram> seatsElsewhere(UUID staffId) {
        Set<Long> ids = trainingRegistrationRepository.findAllByStaffIdAndStatusIn(staffId,
                        List.of(TrainingRegistrationStatus.REGISTERED, TrainingRegistrationStatus.OFFERED)).stream()
                .map(TrainingRegistration::getTrainingId).collect(Collectors.toSet());
        return ids.isEmpty() ? List.of() : trainingProgramRepository.findAllById(ids);
    }

    private static TrainingRegistration of(List<TrainingRegistration> registrations, UUID staffId) {
        return registrations.stream().filter(r -> r.getStaffId().equals(staffId)).findFirst().orElse(null);
    }

    private TrainingRegistration offered(Long sessionId, UUID staffId) {
        return trainingRegistrationRepository.findByTrainingIdAndStaffId(sessionId, staffId)
                .filter(r -> r.getStatus() == TrainingRegistrationStatus.OFFERED)
                .orElseThrow(() -> new BadRequestException("You have no seat offer for this session."));
    }

    private TrainingRegistration onSession(Long sessionId, Long registrationId) {
        return trainingRegistrationRepository.findById(registrationId)
                .filter(r -> r.getTrainingId().equals(sessionId))
                .orElseThrow(() -> new BadRequestException("That person isn't on this session."));
    }

    /** A session still taking registrations from the trainer: scheduled and not started. */
    private static void open(TrainingProgram s, LocalDateTime now) {
        if (s.getStatus() != TrainingSessionStatus.SCHEDULED) {
            throw new BadRequestException("Only a scheduled session takes registrations.");
        }
        if (!now.isBefore(s.startsAt())) {
            throw new BadRequestException("This session has started.");
        }
    }

    private TrainingProgram find(Long id) {
        return trainingProgramRepository.findByTrainingIdAndDeletedFalse(id)
                .orElseThrow(() -> new BadRequestException("This session no longer exists."));
    }

    /** The session, locked so that its seats can't change under this action. */
    private TrainingProgram locked(Long id) {
        return trainingProgramRepository.lockById(id)
                .orElseThrow(() -> new BadRequestException("This session no longer exists."));
    }

    private TrainingProgram manageable(Long id, TrainingViewer viewer) {
        TrainingProgram s = locked(id);
        if (!TrainingServiceImpl.canManage(s, viewer)) {
            throw new ForbiddenRequestException("Only whoever scheduled this session, and HR, can change who is on it.");
        }
        return s;
    }

    private Staff active(UUID staffId, String message) {
        return staffRepository.findAllWithRoleByIdIn(List.of(staffId)).stream()
                .filter(s -> !s.isDeleted() && s.getAccountStatus() == StaffAccountStatus.ACTIVE)
                .findFirst().orElseThrow(() -> new BadRequestException(message));
    }

    private String name(UUID staffId) {
        return staffRepository.findById(staffId).map(Staff::getName).orElse("Someone");
    }

    private static TrainingPersonDto person(Staff s, UUID staffId) {
        TrainingPersonDto dto = new TrainingPersonDto();
        dto.setStaffId(staffId);
        dto.setName(s == null ? "Unknown user" : s.getName());
        if (s != null) {
            OrgChart department = SopAssignmentServiceImpl.department(s);
            dto.setRoleName(s.getRole() == null ? null : s.getRole().getName());
            dto.setDepartmentName(department == null ? null : department.getName());
        }
        return dto;
    }

    /** "********1234": the last 4 characters of an IC or passport number. */
    static String masked(String idNumber) {
        if (idNumber == null || idNumber.isBlank()) return null;
        String digits = idNumber.replace("-", "");
        return digits.length() <= 4 ? digits : "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

}
