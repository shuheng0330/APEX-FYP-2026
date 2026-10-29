package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrainingAudienceCheckDto;
import com.tbm.careerpathlearning.dto.TrainingSessionDto;
import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.dto.TrainingTrainerDto;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingInvitationStatus;
import com.tbm.careerpathlearning.enums.TrainingRegistrationStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionTiming;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAttendance;
import com.tbm.careerpathlearning.model.TrainingInvitation;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.model.TrainingRegistration;
import com.tbm.careerpathlearning.repository.OrgChartRepository;
import com.tbm.careerpathlearning.repository.RoleRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingAttendanceRepository;
import com.tbm.careerpathlearning.repository.TrainingInvitationRepository;
import com.tbm.careerpathlearning.repository.TrainingProgramRepository;
import com.tbm.careerpathlearning.repository.TrainingRegistrationRepository;
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.TrainerAccessService;
import com.tbm.careerpathlearning.service.TrainingAuditService;
import com.tbm.careerpathlearning.service.TrainingRegistrationService;
import com.tbm.careerpathlearning.service.TrainingService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrainingServiceImpl implements TrainingService {

    /** The longest stretch the calendar shows at once: a list of up to a year of sessions. */
    private static final int MAX_RANGE_DAYS = 366;
    private static final int MAX_CAPACITY = 1000;
    private static final int MAX_TITLE = 150;
    private static final int MAX_DESCRIPTION = 4000;
    private static final int MAX_VENUE = 500;
    private static final int MAX_TRAINER_NAME = 100;
    private static final int MAX_REASON = 500;
    private static final DateTimeFormatter NOTICE_DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter NOTICE_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    /** What can't change once a session has started, named as the trainer would say it. */
    private static final Map<String, Function<TrainingProgram, Object>> FIXED_ONCE_STARTED = new LinkedHashMap<>();

    static {
        FIXED_ONCE_STARTED.put("date and times", s -> Arrays.asList(s.getSessionDate(), s.getStartTime(), s.getEndTime()));
        FIXED_ONCE_STARTED.put("kind and delivery", s -> Arrays.asList(s.getKind(), s.getDelivery()));
        FIXED_ONCE_STARTED.put("venue", s -> Arrays.asList(s.getVenue(), s.getLocationName(), s.getLatitude(), s.getLongitude()));
        FIXED_ONCE_STARTED.put("seats", TrainingProgram::getCapacity);
        FIXED_ONCE_STARTED.put("audience",
                s -> Arrays.asList(s.isMandatory(), s.getDepartmentIds(), s.getRoleIds(), s.getStaffIds()));
        FIXED_ONCE_STARTED.put("registration settings", s -> Arrays.asList(s.isSelfRegistration(),
                s.isRegistrationForm(), s.getRegistrationCutoff(), s.isWaitlistEnabled()));
    }

    @Autowired private TrainingProgramRepository trainingProgramRepository;
    @Autowired private TrainingRegistrationRepository trainingRegistrationRepository;
    @Autowired private TrainingInvitationRepository trainingInvitationRepository;
    @Autowired private TrainingAttendanceRepository trainingAttendanceRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private OrgChartRepository orgChartRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TrainerAccessService trainerAccessService;
    @Autowired private NotificationService notificationService;
    @Autowired private TrainingAuditService trainingAuditService;
    @Autowired private TrainingRegistrationService trainingRegistrationService;
    /** Malaysia time, whatever time zone the server runs in. */
    @Autowired private Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<TrainingSessionDto> calendar(LocalDate from, LocalDate to, boolean archived, TrainingViewer viewer) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new BadRequestException("Choose the dates to show.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new BadRequestException("Show at most a year of sessions at a time.");
        }
        List<TrainingProgram> sessions = trainingProgramRepository
                .findAllByDeletedFalseAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(from, to).stream()
                .filter(s -> archived == (s.getStatus() == TrainingSessionStatus.ARCHIVED))
                .toList();
        Lookup lookup = lookup(sessions, viewer);
        return sessions.stream()
                .filter(s -> canSee(s, viewer, lookup))
                .map(s -> toDto(s, viewer, lookup))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingSessionDto> mine(TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> ids = new HashSet<>();
        trainingRegistrationRepository.findAllByStaffIdAndStatusIn(viewer.userId(), List.of(TrainingRegistrationStatus.REGISTERED,
                        TrainingRegistrationStatus.WAITLISTED, TrainingRegistrationStatus.OFFERED))
                .forEach(r -> ids.add(r.getTrainingId()));
        trainingInvitationRepository.findAllByStaffIdAndStatus(viewer.userId(), TrainingInvitationStatus.PENDING)
                .forEach(i -> ids.add(i.getTrainingId()));
        List<TrainingProgram> sessions = ids.isEmpty() ? List.of() : trainingProgramRepository.findAllById(ids).stream()
                .filter(s -> !s.isDeleted() && s.getStatus() == TrainingSessionStatus.SCHEDULED && now.isBefore(s.endsAt()))
                .sorted(Comparator.comparing(TrainingProgram::getSessionDate).thenComparing(TrainingProgram::getStartTime))
                .toList();
        Lookup lookup = lookup(sessions, viewer);
        return sessions.stream().map(s -> toDto(s, viewer, lookup)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingSessionDto> awaitingAttendance(TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<TrainingProgram> started = trainingProgramRepository
                .findAllByDeletedFalseAndStatusAndAttendanceSubmittedAtIsNullAndSessionDateLessThanEqual(
                        TrainingSessionStatus.SCHEDULED, now.toLocalDate()).stream()
                .filter(s -> !now.isBefore(s.startsAt()) && TrainingAttendanceServiceImpl.recorder(s, viewer))
                .toList();
        Lookup lookup = lookup(started, viewer);
        return started.stream()
                .filter(s -> hasAttendees(s, lookup))
                .sorted(Comparator.comparing(TrainingProgram::startsAt))
                .map(s -> toDto(s, viewer, lookup))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TrainingSessionDto get(Long id, TrainingViewer viewer) {
        TrainingProgram session = find(id);
        Lookup lookup = lookup(List.of(session), viewer);
        if (!canSee(session, viewer, lookup)) {
            throw new ForbiddenRequestException("This session isn't open to you.");
        }
        return toDto(session, viewer, lookup);
    }

    @Override
    @Transactional
    public TrainingSessionDto schedule(TrainingSessionRequest request, TrainingViewer viewer) {
        if (!viewer.canSchedule()) {
            throw new ForbiddenRequestException("Only staff with Trainer access, and HR, can schedule sessions.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram session = checked(request, null, now);
        session.setStatus(TrainingSessionStatus.SCHEDULED);
        session.setCreatedBy(viewer.userId());
        session.setCreatedAt(now);
        session.setUpdatedBy(viewer.userId());
        session.setUpdatedAt(now);
        trainingProgramRepository.save(session);
        trainingRegistrationService.inviteAudience(session, viewer.userId());
        return toDto(session, viewer, lookup(List.of(session), viewer));
    }

    @Override
    @Transactional
    public TrainingSessionDto update(Long id, TrainingSessionRequest request, TrainingViewer viewer) {
        TrainingProgram session = manageable(id, viewer);
        if (session.getStatus() != TrainingSessionStatus.SCHEDULED) {
            throw new BadRequestException(session.getStatus() == TrainingSessionStatus.CANCELLED
                    ? "This session was cancelled, so it can't be changed."
                    : "This session is archived. Restore it before changing it.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram changed = checked(request, session, now);
        if (!now.isBefore(session.startsAt())) {
            List<String> fixed = FIXED_ONCE_STARTED.entrySet().stream()
                    .filter(e -> !Objects.equals(e.getValue().apply(session), e.getValue().apply(changed)))
                    .map(Map.Entry::getKey)
                    .toList();
            if (!fixed.isEmpty()) {
                throw new BadRequestException("This session has started, so its " + listed(fixed) + " can't change.");
            }
        }
        if (changed.getTrainingType() != session.getTrainingType() && trainingAttendanceRepository.existsByTrainingId(id)) {
            throw new BadRequestException("Attendance has been recorded for this session, so its training type can't change.");
        }
        List<TrainingRegistration> registrations = trainingRegistrationRepository.findAllByTrainingId(id);
        long booked = registrations.stream().filter(r -> r.getStatus().holdsSeat()).count();
        if (changed.getCapacity() < booked) {
            throw new BadRequestException(booked + " people already have a seat, so the session needs at least "
                    + booked + " seats.");
        }
        boolean moved = !arrangements(session).equals(arrangements(changed));
        boolean moreSeats = changed.getCapacity() > session.getCapacity();
        copy(changed, session);
        session.setUpdatedBy(viewer.userId());
        session.setUpdatedAt(now);
        if (moved) {
            String body = "\"" + session.getTitle() + "\" is now on " + when(session) + ", " + where(session) + ".";
            for (UUID person : toTell(session, registrations)) {
                notificationService.notify(person, NotificationType.SESSION_CHANGED,
                        "Session changed: " + session.getTitle(), body, link(session));
            }
        }
        if (moreSeats) {
            trainingRegistrationService.offerFreeSeats(session); // seats added go to the waitlist first (FR-13-07)
        }
        trainingRegistrationService.inviteAudience(session, viewer.userId()); // people added to the audience (decision E)
        return toDto(session, viewer, lookup(List.of(session), viewer));
    }

    @Override
    @Transactional
    public void delete(Long id, TrainingViewer viewer) {
        TrainingProgram session = manageable(id, viewer);
        if (trainingAttendanceRepository.existsByTrainingId(id)) {
            throw new BadRequestException("Attendance has been recorded for this session, so it can only be archived.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (trainingRegistrationRepository.existsByTrainingId(id) || trainingInvitationRepository.existsByTrainingId(id)) {
            boolean cancellable = session.getStatus() == TrainingSessionStatus.SCHEDULED && now.isBefore(session.startsAt());
            throw new BadRequestException("Staff have registered for or been invited to this session, so it can't be deleted. "
                    + (cancellable ? "Cancel it instead, and they are told." : "Archive it instead."));
        }
        session.setDeleted(true);
        session.setUpdatedBy(viewer.userId());
        session.setUpdatedAt(now);
        audit(viewer, TrainingAuditAction.SESSION_DELETED, session, null);
    }

    @Override
    @Transactional
    public TrainingSessionDto cancel(Long id, String reason, TrainingViewer viewer) {
        TrainingProgram session = manageable(id, viewer);
        if (session.getStatus() != TrainingSessionStatus.SCHEDULED) {
            throw new BadRequestException(session.getStatus() == TrainingSessionStatus.CANCELLED
                    ? "This session is already cancelled." : "This session is archived.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(session.startsAt())) {
            throw new BadRequestException("This session has already started, so it can't be cancelled.");
        }
        String why = trimmed(reason);
        if (why == null) {
            throw new BadRequestException("Give a reason. It goes in the notice to everyone registered or invited.");
        }
        if (why.length() > MAX_REASON) {
            throw new BadRequestException("Keep the reason to " + MAX_REASON + " characters.");
        }
        session.setStatus(TrainingSessionStatus.CANCELLED);
        session.setCancelReason(why);
        session.setCancelledAt(now);
        session.setCancelledBy(viewer.userId());
        session.setUpdatedBy(viewer.userId());
        session.setUpdatedAt(now);
        String body = "\"" + session.getTitle() + "\" on " + when(session) + " is cancelled. Reason: " + why;
        for (UUID person : toTell(session, trainingRegistrationRepository.findAllByTrainingId(id))) {
            notificationService.notify(person, NotificationType.SESSION_CANCELLED,
                    "Session cancelled: " + session.getTitle(), body, link(session));
        }
        audit(viewer, TrainingAuditAction.SESSION_CANCELLED, session, why);
        return toDto(session, viewer, lookup(List.of(session), viewer));
    }

    @Override
    @Transactional
    public TrainingSessionDto archive(Long id, TrainingViewer viewer) {
        TrainingProgram session = manageable(id, viewer);
        LocalDateTime now = LocalDateTime.now(clock);
        if (session.getStatus() == TrainingSessionStatus.ARCHIVED) {
            throw new BadRequestException("This session is already archived.");
        }
        if (session.getStatus() == TrainingSessionStatus.SCHEDULED && now.isBefore(session.endsAt())) {
            throw new BadRequestException("Only a session that has ended, or was cancelled, can be archived. "
                    + "If it won't go ahead, cancel it.");
        }
        boolean attendees = trainingAttendanceRepository.existsByTrainingId(id)
                || trainingRegistrationRepository.findAllByTrainingId(id).stream()
                .anyMatch(r -> r.getStatus() == TrainingRegistrationStatus.REGISTERED);
        if (session.getStatus() == TrainingSessionStatus.SCHEDULED && session.getAttendanceSubmittedAt() == null
                && attendees) { // decision D, 28 Oct 2026
            throw new BadRequestException("Submit the attendance first, then archive the session.");
        }
        session.setStatus(TrainingSessionStatus.ARCHIVED);
        session.setArchivedAt(now);
        session.setUpdatedBy(viewer.userId());
        session.setUpdatedAt(now);
        audit(viewer, TrainingAuditAction.SESSION_ARCHIVED, session, null);
        return toDto(session, viewer, lookup(List.of(session), viewer));
    }

    @Override
    @Transactional
    public TrainingSessionDto restore(Long id, TrainingViewer viewer) {
        TrainingProgram session = manageable(id, viewer);
        if (session.getStatus() != TrainingSessionStatus.ARCHIVED) {
            throw new BadRequestException("This session isn't archived.");
        }
        session.setStatus(session.getCancelledAt() != null ? TrainingSessionStatus.CANCELLED : TrainingSessionStatus.SCHEDULED);
        session.setArchivedAt(null);
        session.setUpdatedBy(viewer.userId());
        session.setUpdatedAt(LocalDateTime.now(clock));
        audit(viewer, TrainingAuditAction.SESSION_RESTORED, session, null);
        return toDto(session, viewer, lookup(List.of(session), viewer));
    }

    @Override
    @Transactional(readOnly = true)
    public TrainingAudienceCheckDto checkAudience(TrainingSessionRequest request) {
        TrainingProgram session = new TrainingProgram();
        session.getDepartmentIds().addAll(ids(request.getDepartmentIds()));
        session.getRoleIds().addAll(ids(request.getRoleIds()));
        session.getStaffIds().addAll(ids(request.getStaffIds()));
        session.setTrainerId(request.getTrainerId());
        int capacity = request.getCapacity() == null ? 0 : request.getCapacity();
        int size = audienceSize(session, staffRepository.findAllActiveWithRole());
        TrainingAudienceCheckDto dto = new TrainingAudienceCheckDto();
        dto.setAudienceSize(size);
        dto.setCapacity(capacity);
        dto.setWarning(capacityWarning(request.isMandatory(), size, capacity));
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainingTrainerDto> trainers() {
        return trainerAccessService.trainersAndHr().stream().map(s -> {
            OrgChart department = SopAssignmentServiceImpl.department(s);
            TrainingTrainerDto dto = new TrainingTrainerDto();
            dto.setId(s.getId());
            dto.setName(s.getName());
            dto.setRoleName(s.getRole() == null ? null : s.getRole().getName());
            dto.setDepartmentName(department == null ? null : department.getName());
            return dto;
        }).toList();
    }

    /**
     * Whether the staff member is in the session's target audience. Everyone is, when no audience was chosen
     * (FR-12-03).
     */
    static boolean inAudience(TrainingProgram session, Staff staff) {
        if (!session.hasAudience()) {
            return true;
        }
        OrgChart department = SopAssignmentServiceImpl.department(staff);
        return session.getStaffIds().contains(staff.getId())
                || (staff.getRole() != null && session.getRoleIds().contains(staff.getRole().getId()))
                || (department != null && session.getDepartmentIds().contains(department.getId()));
    }

    /** FR-12-15: a mandatory session that can't seat its whole audience, with how many more runs would. */
    static String capacityWarning(boolean mandatory, int audience, int capacity) {
        if (!mandatory || capacity < 1 || audience <= capacity) {
            return null;
        }
        int others = audience - capacity;
        int moreRuns = (others + capacity - 1) / capacity;
        return "This mandatory session is for " + audience + " people but has " + capacity
                + (capacity == 1 ? " seat" : " seats") + ". Schedule "
                + (moreRuns == 1 ? "another run" : moreRuns + " more runs") + " for the other " + others
                + (others == 1 ? " person." : " people.");
    }

    // --- checks -------------------------------------------------------------------

    /**
     * The session as the request would make it, after checking every rule (FR-12-01 to FR-12-04, FR-12-12,
     * FR-12-13, FR-15-05, FR-17-01). {@code current} is the session being changed, or null for a new one.
     */
    private TrainingProgram checked(TrainingSessionRequest r, TrainingProgram current, LocalDateTime now) {
        TrainingProgram s = new TrainingProgram();
        String title = trimmed(r.getTitle());
        if (title == null) {
            throw new BadRequestException("Enter a title.");
        }
        if (title.length() > MAX_TITLE) {
            throw new BadRequestException("Keep the title to " + MAX_TITLE + " characters.");
        }
        s.setTitle(title);
        String description = trimmed(r.getDescription());
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new BadRequestException("Keep the description to 4,000 characters.");
        }
        s.setDescription(description);

        if (r.getKind() == null) {
            throw new BadRequestException("Choose whether it's a training session or a staff sharing session.");
        }
        if (r.getDelivery() == null) {
            throw new BadRequestException("Choose how it's delivered: Physical, Hybrid or Online.");
        }
        s.setKind(r.getKind());
        s.setDelivery(r.getDelivery());
        // Only Physical and Hybrid training is classified, for the training KPI (FR-17-01, FR-17-02).
        boolean typed = r.getKind() == TrainingSessionKind.TRAINING && r.getDelivery() != TrainingDelivery.ONLINE;
        if (typed && r.getTrainingType() == null) {
            throw new BadRequestException("Choose the training type: Product Training, Skill-Based Training or Other.");
        }
        s.setTrainingType(typed ? r.getTrainingType() : null);

        if (r.getDate() == null || r.getStartTime() == null || r.getEndTime() == null) {
            throw new BadRequestException("Enter the date and the start and end times.");
        }
        s.setSessionDate(r.getDate());
        s.setStartTime(r.getStartTime().truncatedTo(ChronoUnit.MINUTES));
        s.setEndTime(r.getEndTime().truncatedTo(ChronoUnit.MINUTES));
        if (!s.getEndTime().isAfter(s.getStartTime())) {
            throw new BadRequestException("The session must end after it starts, on the same day.");
        }
        boolean newStart = current == null || !s.startsAt().equals(current.startsAt());
        if (newStart && !s.startsAt().isAfter(now)) {
            throw new BadRequestException("A session can't start in the past.");
        }

        boolean inPerson = r.getDelivery() != TrainingDelivery.ONLINE;
        String venue = trimmed(r.getVenue());
        if (venue == null && inPerson) {
            throw new BadRequestException("Enter the venue.");
        }
        if (venue != null && venue.length() > MAX_VENUE) {
            throw new BadRequestException("Keep the venue to " + MAX_VENUE + " characters.");
        }
        s.setVenue(venue);
        boolean pinned = inPerson && r.getLatitude() != null && r.getLongitude() != null;
        s.setLocationName(pinned ? trimmed(r.getLocationName()) : null);
        s.setLatitude(pinned ? r.getLatitude() : null);
        s.setLongitude(pinned ? r.getLongitude() : null);

        if (r.getCapacity() == null || r.getCapacity() < 1 || r.getCapacity() > MAX_CAPACITY) {
            throw new BadRequestException("Seats must be between 1 and 1,000.");
        }
        s.setCapacity(r.getCapacity());
        s.setMandatory(r.isMandatory());
        setTrainer(s, r, current);
        s.getDepartmentIds().addAll(departments(r.getDepartmentIds(), current));
        s.getRoleIds().addAll(roles(r.getRoleIds(), current));
        s.getStaffIds().addAll(people(r.getStaffIds(), current));

        s.setSelfRegistration(r.isSelfRegistration());
        s.setRegistrationForm(r.isRegistrationForm());
        s.setWaitlistEnabled(r.isWaitlistEnabled());
        LocalDate cutoff = r.getRegistrationCutoff();
        if (cutoff != null) {
            if (cutoff.isAfter(s.getSessionDate())) {
                throw new BadRequestException("The registration cut-off must be on or before the session date.");
            }
            boolean newCutoff = current == null || !cutoff.equals(current.getRegistrationCutoff());
            if (newCutoff && cutoff.isBefore(now.toLocalDate())) {
                throw new BadRequestException("The registration cut-off can't be in the past.");
            }
        }
        s.setRegistrationCutoff(cutoff);
        return s;
    }

    /**
     * Who runs or presents the session: someone with Trainer access or HR, or for a training session an external
     * trainer (FR-12-01, FR-15-05). A trainer already on the session stays, even if they have since lost access.
     */
    private void setTrainer(TrainingProgram s, TrainingSessionRequest r, TrainingProgram current) {
        boolean sharing = s.getKind() == TrainingSessionKind.STAFF_SHARING;
        if (r.getTrainerId() != null) {
            boolean unchanged = current != null && r.getTrainerId().equals(current.getTrainerId());
            if (!unchanged && trainerAccessService.trainersAndHr().stream().noneMatch(t -> t.getId().equals(r.getTrainerId()))) {
                String name = staffRepository.findById(r.getTrainerId()).map(Staff::getName).orElse("This person");
                throw new BadRequestException(name + (sharing ? " can't present" : " can't run")
                        + " sessions: only staff with Trainer access, and HR, can. The Super Admin can give Trainer access.");
            }
            s.setTrainerId(r.getTrainerId());
            return;
        }
        String external = trimmed(r.getTrainerName());
        if (external == null) {
            throw new BadRequestException(sharing ? "Choose who presents the session." : "Choose who runs the session.");
        }
        if (sharing) {
            throw new BadRequestException("A staff sharing session is presented by a staff member with Trainer access, "
                    + "not an external trainer.");
        }
        if (external.length() > MAX_TRAINER_NAME) {
            throw new BadRequestException("Keep the trainer's name to " + MAX_TRAINER_NAME + " characters.");
        }
        s.setTrainerName(external);
    }

    /** The chosen departments; any newly chosen must still exist. Ones kept from before stay even if since removed. */
    private Set<Long> departments(List<Long> requested, TrainingProgram current) {
        Set<Long> ids = ids(requested);
        Set<Long> added = added(ids, current == null ? Set.of() : current.getDepartmentIds());
        if (!added.isEmpty() && orgChartRepository.findAllById(added).stream()
                .filter(o -> !o.isDeleted() && o.getType() == OrgChartType.D).count() < added.size()) {
            throw new BadRequestException("A chosen department no longer exists. Choose the audience again.");
        }
        return ids;
    }

    private Set<Long> roles(List<Long> requested, TrainingProgram current) {
        Set<Long> ids = ids(requested);
        Set<Long> added = added(ids, current == null ? Set.of() : current.getRoleIds());
        if (!added.isEmpty() && roleRepository.findAllById(added).stream().filter(r -> !r.isDeleted()).count() < added.size()) {
            throw new BadRequestException("A chosen job role no longer exists. Choose the audience again.");
        }
        return ids;
    }

    private Set<UUID> people(List<UUID> requested, TrainingProgram current) {
        Set<UUID> ids = ids(requested);
        Set<UUID> added = added(ids, current == null ? Set.of() : current.getStaffIds());
        if (!added.isEmpty() && staffRepository.findAllByIsDeletedIsFalseAndIdIn(added).stream()
                .filter(st -> st.getAccountStatus() == StaffAccountStatus.ACTIVE).count() < added.size()) {
            throw new BadRequestException("Someone chosen for the audience has left or their account is inactive. "
                    + "Choose the audience again.");
        }
        return ids;
    }

    private TrainingProgram find(Long id) {
        return trainingProgramRepository.findByTrainingIdAndDeletedFalse(id)
                .orElseThrow(() -> new BadRequestException("This session no longer exists."));
    }

    private TrainingProgram manageable(Long id, TrainingViewer viewer) {
        TrainingProgram session = trainingProgramRepository.lockById(id)
                .orElseThrow(() -> new BadRequestException("This session no longer exists."));
        if (!canManage(session, viewer)) {
            throw new ForbiddenRequestException("Only whoever scheduled this session, and HR, can change it.");
        }
        return session;
    }

    static boolean canManage(TrainingProgram session, TrainingViewer viewer) {
        return viewer.hr() || (viewer.trainer() && viewer.userId().equals(session.getCreatedBy()));
    }

    /**
     * Trainers, HR and the Super Admin see every session. Other staff see the scheduled sessions meant for them,
     * and any session they registered for, even once it is cancelled.
     */
    private static boolean canSee(TrainingProgram session, TrainingViewer viewer, Lookup lookup) {
        if (viewer.seesAllSessions()) {
            return true;
        }
        if (session.getStatus() == TrainingSessionStatus.ARCHIVED) {
            return false;
        }
        boolean registered = lookup.registrations().getOrDefault(session.getTrainingId(), List.of()).stream()
                .anyMatch(r -> r.getStaffId().equals(viewer.userId()));
        boolean invited = lookup.myInvitations().containsKey(session.getTrainingId());
        Staff me = lookup.activeById().get(viewer.userId());
        return registered || invited
                || (session.getStatus() == TrainingSessionStatus.SCHEDULED && me != null && inAudience(session, me));
    }

    /** Someone is registered or has attendance recorded, so the session has attendance to submit. */
    private static boolean hasAttendees(TrainingProgram session, Lookup lookup) {
        return lookup.attended().contains(session.getTrainingId())
                || lookup.registrations().getOrDefault(session.getTrainingId(), List.of()).stream()
                .anyMatch(r -> r.getStatus() == TrainingRegistrationStatus.REGISTERED);
    }

    /** Active staff in the audience, not counting whoever runs the session. */
    private static int audienceSize(TrainingProgram session, Collection<Staff> active) {
        return (int) active.stream()
                .filter(s -> !s.getId().equals(session.getTrainerId()) && inAudience(session, s))
                .count();
    }

    /** Everyone registered, waitlisted or offered a seat, and everyone invited who hasn't answered. */
    private Set<UUID> toTell(TrainingProgram session, List<TrainingRegistration> registrations) {
        Set<UUID> people = new LinkedHashSet<>();
        registrations.stream().filter(r -> r.getStatus().isActive()).map(TrainingRegistration::getStaffId).forEach(people::add);
        trainingInvitationRepository.findAllByTrainingIdAndStatus(session.getTrainingId(), TrainingInvitationStatus.PENDING)
                .stream().map(TrainingInvitation::getStaffId).forEach(people::add);
        return people;
    }

    private void audit(TrainingViewer viewer, TrainingAuditAction action, TrainingProgram session, String reason) {
        trainingAuditService.record(viewer.userId(), action, TrainingAuditTarget.SESSION,
                String.valueOf(session.getTrainingId()), reason, "\"" + session.getTitle() + "\" on " + when(session));
    }

    // --- the calendar's view --------------------------------------------------------

    /** What showing a set of sessions needs, loaded once for all of them. */
    private record Lookup(Map<UUID, Staff> activeById, Map<Long, List<TrainingRegistration>> registrations,
                          Set<Long> invited, Set<Long> attended, Map<Long, String> departments,
                          Map<Long, String> roles, Map<UUID, String> names,
                          Map<Long, TrainingInvitation> myInvitations, List<TrainingProgram> mySeats,
                          Map<Long, TrainingAttendance> myAttendance) {
    }

    private Lookup lookup(List<TrainingProgram> sessions, TrainingViewer viewer) {
        List<Long> ids = sessions.stream().map(TrainingProgram::getTrainingId).toList();
        Map<UUID, Staff> active = new LinkedHashMap<>();
        staffRepository.findAllActiveWithRole().forEach(s -> active.put(s.getId(), s));
        Map<Long, List<TrainingRegistration>> registrations = ids.isEmpty() ? Map.of()
                : trainingRegistrationRepository.findAllByTrainingIdIn(ids).stream()
                .collect(Collectors.groupingBy(TrainingRegistration::getTrainingId));
        Set<Long> invited = ids.isEmpty() ? Set.of() : new HashSet<>(trainingInvitationRepository.findInvitedTrainingIds(ids));
        Set<Long> attended = ids.isEmpty() ? Set.of() : new HashSet<>(trainingAttendanceRepository.findRecordedTrainingIds(ids));

        Set<Long> departmentIds = sessions.stream().flatMap(s -> s.getDepartmentIds().stream()).collect(Collectors.toSet());
        Set<Long> roleIds = sessions.stream().flatMap(s -> s.getRoleIds().stream()).collect(Collectors.toSet());
        Map<Long, String> departments = departmentIds.isEmpty() ? Map.of() : orgChartRepository.findAllById(departmentIds)
                .stream().collect(Collectors.toMap(OrgChart::getId, o -> Objects.toString(o.getName(), "")));
        Map<Long, String> roles = roleIds.isEmpty() ? Map.of() : roleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, TrainingServiceImpl::roleLabel));
        // Trainers, schedulers and named staff, whether or not they are still active.
        Set<UUID> people = new HashSet<>();
        for (TrainingProgram s : sessions) {
            people.addAll(s.getStaffIds());
            if (s.getTrainerId() != null) {
                people.add(s.getTrainerId());
            }
            if (s.getCreatedBy() != null) {
                people.add(s.getCreatedBy());
            }
        }
        Map<UUID, String> names = people.isEmpty() ? Map.of() : staffRepository.findAllWithRoleByIdIn(people).stream()
                .collect(Collectors.toMap(Staff::getId, s -> Objects.toString(s.getName(), "Unknown user")));
        // The viewer's own invitations, and the sessions they have a seat on, for clashes (decision B).
        Map<Long, TrainingInvitation> myInvitations = ids.isEmpty() ? Map.of()
                : trainingInvitationRepository.findAllByStaffIdAndTrainingIdIn(viewer.userId(), ids).stream()
                .collect(Collectors.toMap(TrainingInvitation::getTrainingId, i -> i, (a, b) -> a));
        Set<Long> seatIds = trainingRegistrationRepository.findAllByStaffIdAndStatusIn(viewer.userId(),
                        List.of(TrainingRegistrationStatus.REGISTERED, TrainingRegistrationStatus.OFFERED)).stream()
                .map(TrainingRegistration::getTrainingId).collect(Collectors.toSet());
        List<TrainingProgram> mySeats = seatIds.isEmpty() ? List.of() : trainingProgramRepository.findAllById(seatIds);
        Map<Long, TrainingAttendance> myAttendance = ids.isEmpty() ? Map.of()
                : trainingAttendanceRepository.findAllByStaffIdAndTrainingIdIn(viewer.userId(), ids).stream()
                .collect(Collectors.toMap(TrainingAttendance::getTrainingId, a -> a, (a, b) -> a));
        return new Lookup(active, registrations, invited, attended, departments, roles, names, myInvitations, mySeats,
                myAttendance);
    }

    private TrainingSessionDto toDto(TrainingProgram s, TrainingViewer viewer, Lookup lookup) {
        Long id = s.getTrainingId();
        List<TrainingRegistration> registrations = lookup.registrations().getOrDefault(id, List.of());
        int booked = (int) registrations.stream().filter(r -> r.getStatus().holdsSeat()).count();
        int audience = audienceSize(s, lookup.activeById().values());
        boolean manage = canManage(s, viewer);
        boolean attended = lookup.attended().contains(id);
        LocalDateTime now = LocalDateTime.now(clock);

        TrainingSessionDto dto = new TrainingSessionDto();
        dto.setId(id);
        dto.setTitle(s.getTitle());
        dto.setDescription(s.getDescription());
        dto.setKind(s.getKind());
        dto.setDelivery(s.getDelivery());
        dto.setTrainingType(s.getTrainingType());
        dto.setDate(s.getSessionDate());
        dto.setStartTime(s.getStartTime());
        dto.setEndTime(s.getEndTime());
        dto.setDurationMinutes(s.durationMinutes());
        dto.setVenue(s.getVenue());
        dto.setLocationName(s.getLocationName());
        dto.setLatitude(s.getLatitude());
        dto.setLongitude(s.getLongitude());
        dto.setCapacity(s.getCapacity());
        dto.setBooked(booked);
        dto.setSeatsLeft(Math.max(0, s.getCapacity() - booked));
        dto.setWaitlisted((int) registrations.stream()
                .filter(r -> r.getStatus() == TrainingRegistrationStatus.WAITLISTED).count());
        dto.setMandatory(s.isMandatory());
        dto.setTrainerId(s.getTrainerId());
        dto.setExternalTrainer(s.getTrainerId() == null);
        dto.setTrainerName(s.getTrainerId() == null ? s.getTrainerName()
                : lookup.names().getOrDefault(s.getTrainerId(), "Unknown user"));
        if (manage) {
            dto.setDepartmentIds(s.getDepartmentIds().stream().sorted().toList());
            dto.setRoleIds(s.getRoleIds().stream().sorted().toList());
            dto.setStaffIds(new ArrayList<>(s.getStaffIds()));
        }
        dto.setAudience(audienceLabel(s, lookup));
        dto.setAudienceSize(audience);
        if (s.getStatus() == TrainingSessionStatus.SCHEDULED && s.timing(now) == TrainingSessionTiming.UPCOMING) {
            dto.setCapacityWarning(capacityWarning(s.isMandatory(), audience, s.getCapacity()));
        }
        dto.setSelfRegistration(s.isSelfRegistration());
        dto.setRegistrationForm(s.isRegistrationForm());
        dto.setRegistrationCutoff(s.getRegistrationCutoff());
        dto.setWaitlistEnabled(s.isWaitlistEnabled());
        dto.setStatus(s.getStatus());
        dto.setTiming(s.timing(now));
        dto.setCancelReason(s.getCancelReason());
        dto.setCancelledAt(s.getCancelledAt());
        dto.setArchivedAt(s.getArchivedAt());
        dto.setCreatedByName(s.getCreatedBy() == null ? null : lookup.names().getOrDefault(s.getCreatedBy(), "Unknown user"));
        dto.setCreatedAt(s.getCreatedAt());
        dto.setCanManage(manage);
        dto.setCanDelete(manage && registrations.isEmpty() && !lookup.invited().contains(id) && !attended);
        dto.setAttendanceRecorded(attended);
        dto.setCanSeePeople(manage || viewer.superAdmin() || viewer.userId().equals(s.getTrainerId()));
        dto.setCanRecordAttendance(s.getStatus() == TrainingSessionStatus.SCHEDULED && !now.isBefore(s.startsAt())
                && TrainingAttendanceServiceImpl.recorder(s, viewer));
        dto.setAttendanceSubmittedAt(s.getAttendanceSubmittedAt());
        TrainingAttendance myAttendance = lookup.myAttendance().get(id);
        if (myAttendance != null && s.getAttendanceSubmittedAt() != null) { // shown once it is final (FR-14-09)
            dto.setMyAttendance(myAttendance.getStatus().name());
            dto.setMyQuizScore(myAttendance.getQuizScore());
        }
        viewersPlace(dto, s, viewer, lookup, registrations, now);
        return dto;
    }

    /** The viewer's own place on the session, and whether they can register now (FR-13-04, FR-13-05). */
    private static void viewersPlace(TrainingSessionDto dto, TrainingProgram s, TrainingViewer viewer, Lookup lookup,
                             List<TrainingRegistration> registrations, LocalDateTime now) {
        TrainingRegistration mine = registrations.stream().filter(r -> r.getStaffId().equals(viewer.userId()))
                .findFirst().orElse(null);
        TrainingInvitation invitation = lookup.myInvitations().get(s.getTrainingId());
        TrainingRegistrationRules.Seats seats = TrainingRegistrationRules.Seats.of(s, registrations);
        if (mine != null) {
            dto.setMyStatus(mine.getStatus());
            if (mine.getStatus() == TrainingRegistrationStatus.WAITLISTED) {
                dto.setMyWaitlistPosition(seats.waiting().indexOf(mine) + 1);
            }
            if (mine.getStatus() == TrainingRegistrationStatus.OFFERED) {
                dto.setMyOfferExpiresAt(mine.getOfferExpiresAt());
            }
        }
        dto.setMyInvitation(invitation == null ? null : invitation.getStatus());
        Staff me = lookup.activeById().get(viewer.userId());
        String blocker = me == null ? "Your account isn't active, so you can't register."
                : TrainingRegistrationRules.blocker(s, viewer.userId(), inAudience(s, me), mine, invitation,
                TrainingRegistrationRules.clash(s, lookup.mySeats()), seats, now);
        dto.setCanRegister(blocker == null);
        dto.setRegisterBlocker(blocker);
        dto.setWouldWaitlist(TrainingRegistrationRules.joinsWaitlist(s, seats));
    }

    /** "Everyone", or the chosen departments, job roles and people. */
    private static String audienceLabel(TrainingProgram s, Lookup lookup) {
        if (!s.hasAudience()) {
            return "Everyone";
        }
        List<String> parts = new ArrayList<>();
        s.getDepartmentIds().stream().map(lookup.departments()::get).filter(Objects::nonNull)
                .sorted(String.CASE_INSENSITIVE_ORDER).forEach(parts::add);
        s.getRoleIds().stream().map(lookup.roles()::get).filter(Objects::nonNull)
                .sorted(String.CASE_INSENSITIVE_ORDER).forEach(parts::add);
        List<String> people = s.getStaffIds().stream().map(lookup.names()::get).filter(Objects::nonNull)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (people.size() > 3) {
            parts.add(String.join(", ", people.subList(0, 3)) + " and " + (people.size() - 3) + " more");
        } else {
            parts.addAll(people);
        }
        return parts.isEmpty() ? "Selected staff" : String.join("; ", parts);
    }

    /** "Technician (Service Department)", as the Assign Training page names job roles. */
    private static String roleLabel(Role role) {
        OrgChart node = role.getOrgChart();
        boolean department = node != null && node.getType() == OrgChartType.D && !node.isDeleted();
        return Objects.toString(role.getName(), "") + (department ? " (" + node.getName() + ")" : "");
    }

    // --- small helpers ----------------------------------------------------------------

    /** What people registered or invited need to know about: when, where and how it is delivered. */
    private static List<Object> arrangements(TrainingProgram s) {
        return Arrays.asList(s.getSessionDate(), s.getStartTime(), s.getEndTime(), s.getDelivery(), s.getVenue());
    }

    private static void copy(TrainingProgram from, TrainingProgram to) {
        to.setTitle(from.getTitle());
        to.setDescription(from.getDescription());
        to.setKind(from.getKind());
        to.setDelivery(from.getDelivery());
        to.setTrainingType(from.getTrainingType());
        to.setSessionDate(from.getSessionDate());
        to.setStartTime(from.getStartTime());
        to.setEndTime(from.getEndTime());
        to.setVenue(from.getVenue());
        to.setLocationName(from.getLocationName());
        to.setLatitude(from.getLatitude());
        to.setLongitude(from.getLongitude());
        to.setCapacity(from.getCapacity());
        to.setMandatory(from.isMandatory());
        to.setTrainerId(from.getTrainerId());
        to.setTrainerName(from.getTrainerName());
        replace(to.getDepartmentIds(), from.getDepartmentIds());
        replace(to.getRoleIds(), from.getRoleIds());
        replace(to.getStaffIds(), from.getStaffIds());
        to.setSelfRegistration(from.isSelfRegistration());
        to.setRegistrationForm(from.isRegistrationForm());
        to.setRegistrationCutoff(from.getRegistrationCutoff());
        to.setWaitlistEnabled(from.isWaitlistEnabled());
    }

    /** Changes a stored collection in place, so only the differences are saved. */
    private static <T> void replace(Set<T> target, Set<T> values) {
        target.retainAll(values);
        target.addAll(values);
    }

    private static <T> Set<T> ids(List<T> requested) {
        return requested == null ? new LinkedHashSet<>()
                : requested.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static <T> Set<T> added(Set<T> chosen, Set<T> before) {
        Set<T> added = new HashSet<>(chosen);
        added.removeAll(before);
        return added;
    }

    /** "Tue 4 Nov 2026, 09:00 to 12:00" */
    static String when(TrainingProgram s) {
        return s.getSessionDate().format(NOTICE_DATE) + ", " + s.getStartTime().format(NOTICE_TIME) + " to "
                + s.getEndTime().format(NOTICE_TIME);
    }

    static String where(TrainingProgram s) {
        return switch (s.getDelivery()) {
            case PHYSICAL -> "at " + s.getVenue();
            case HYBRID -> "at " + s.getVenue() + " or online";
            case ONLINE -> s.getVenue() == null ? "online" : "online: " + s.getVenue();
        };
    }

    static String link(TrainingProgram s) {
        return "/training/calendar?session=" + s.getTrainingId();
    }

    /** "a", "a and b", "a, b and c" */
    private static String listed(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    private static String trimmed(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
