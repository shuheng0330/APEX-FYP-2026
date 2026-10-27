package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.TrainingAudienceCheckDto;
import com.tbm.careerpathlearning.dto.TrainingSessionDto;
import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.dto.TrainingTrainerDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingInvitationStatus;
import com.tbm.careerpathlearning.enums.TrainingRegistrationStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionTiming;
import com.tbm.careerpathlearning.enums.TrainingType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainerAccessGrant;
import com.tbm.careerpathlearning.model.TrainingAttendance;
import com.tbm.careerpathlearning.model.TrainingAuditLog;
import com.tbm.careerpathlearning.model.TrainingInvitation;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.model.TrainingRegistration;
import com.tbm.careerpathlearning.repository.TrainingAttendanceRepository;
import com.tbm.careerpathlearning.repository.TrainingInvitationRepository;
import com.tbm.careerpathlearning.repository.TrainingRegistrationRepository;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/** Scheduling, changing, cancelling, archiving and deleting training sessions (FR-12, FR-15-05, FR-17-01). */
class TrainingSessionTest {

    private SopTestStore store;
    private TrainingServiceImpl sessions;
    private Role salesExecutive;
    private Staff hr, weiMing, karim, aisyah, ravi, farid;
    private TrainingViewer asHr, asWeiMing, asKarim, asAisyah;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        sessions = store.wire(new TrainingServiceImpl());
        OrgChart sales = store.department(10L, "Sales Department");
        OrgChart service = store.department(11L, "Service Department");
        OrgChart people = store.department(12L, "Human Resources");
        salesExecutive = store.role(20L, "Sales Executive", sales);
        Role technician = store.role(21L, "Technician", service);
        Role trainer = store.role(22L, "Trainer", people);
        Role hrExecutive = store.role(23L, "HR Executive", people);
        store.permit(trainer, AuthorityName.CAN_MANAGE_TRAINING);
        store.permit(hrExecutive, AuthorityName.CAN_ASSIGN_TRAINING);
        hr = store.person("Hana Yusof", hrExecutive);
        weiMing = store.person("Tan Wei Ming", trainer);
        karim = store.person("Karim Abdullah", trainer);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        ravi = store.person("Ravi Kumar", salesExecutive);
        farid = store.person("Ahmad Farid", technician);
        asHr = new TrainingViewer(hr.getId(), false, true, false);
        asWeiMing = new TrainingViewer(weiMing.getId(), true, false, false);
        asKarim = new TrainingViewer(karim.getId(), true, false, false);
        asAisyah = new TrainingViewer(aisyah.getId(), false, false, false);
        today = store.today(); // Monday 12 Oct 2026, 10:00 in Malaysia
    }

    /** A Physical product training for 20, Tuesday 20 Oct 2026, 09:00 to 12:00, run by Tan Wei Ming, for everyone. */
    private TrainingSessionRequest launch() {
        TrainingSessionRequest r = new TrainingSessionRequest();
        r.setTitle("Inverter Series 2026 Launch");
        r.setKind(TrainingSessionKind.TRAINING);
        r.setDelivery(TrainingDelivery.PHYSICAL);
        r.setTrainingType(TrainingType.PRODUCT);
        r.setDate(today.plusDays(8));
        r.setStartTime(LocalTime.of(9, 0));
        r.setEndTime(LocalTime.of(12, 0));
        r.setVenue("Conference Hall B, HQ");
        r.setCapacity(20);
        r.setTrainerId(weiMing.getId());
        return r;
    }

    private TrainingSessionRequest launch(Consumer<TrainingSessionRequest> change) {
        TrainingSessionRequest r = launch();
        change.accept(r);
        return r;
    }

    private void refused(String message, Consumer<TrainingSessionRequest> change) {
        BadRequestException e = assertThrows(BadRequestException.class, () -> sessions.schedule(launch(change), asWeiMing));
        assertEquals(message, e.getMessage());
    }

    private void registration(Long sessionId, Staff person, TrainingRegistrationStatus status) {
        TrainingRegistration r = new TrainingRegistration();
        r.setTrainingId(sessionId);
        r.setStaffId(person.getId());
        r.setStatus(status);
        r.setRegisteredAt(LocalDateTime.now(store.clock));
        store.repository(TrainingRegistrationRepository.class).save(r);
    }

    private void invite(Long sessionId, Staff person) {
        TrainingInvitation i = new TrainingInvitation();
        i.setTrainingId(sessionId);
        i.setStaffId(person.getId());
        i.setInvitedBy(hr.getId());
        i.setStatus(TrainingInvitationStatus.PENDING);
        store.repository(TrainingInvitationRepository.class).save(i);
    }

    private void attended(Long sessionId, Staff person) {
        TrainingAttendance a = new TrainingAttendance();
        a.setTrainingId(sessionId);
        a.setStaffId(person.getId());
        store.repository(TrainingAttendanceRepository.class).save(a);
    }

    /** Moves the clock to this time in Malaysia. */
    private void at(LocalDate date, int hour, int minute) {
        store.clock.set(LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ClockConfig.ZONE).toInstant());
    }

    private static List<Long> ids(List<TrainingSessionDto> sessions) {
        return sessions.stream().map(TrainingSessionDto::getId).toList();
    }

    /** Who was notified, in order. */
    private List<UUID> told() {
        return store.notifications.stream().map(SopTestStore.Sent::recipient).toList();
    }

    @Test
    @DisplayName("A trainer schedules a session in their own name: Scheduled, upcoming, every seat free, for everyone")
    void schedule() {
        TrainingSessionDto dto = sessions.schedule(launch(), asWeiMing);

        assertEquals(TrainingSessionStatus.SCHEDULED, dto.getStatus());
        assertEquals(TrainingSessionTiming.UPCOMING, dto.getTiming());
        assertEquals(180, dto.getDurationMinutes());
        assertEquals(0, dto.getBooked());
        assertEquals(20, dto.getSeatsLeft());
        assertEquals("Tan Wei Ming", dto.getTrainerName());
        assertEquals("Tan Wei Ming", dto.getCreatedByName());
        assertEquals("Everyone", dto.getAudience());
        assertEquals(5, dto.getAudienceSize(), "the six active staff, not counting the trainer");
        assertNull(dto.getCapacityWarning());
        assertTrue(dto.isCanManage());
        assertTrue(dto.isCanDelete());
        TrainingProgram saved = store.sessions.get(dto.getId());
        assertEquals(weiMing.getId(), saved.getCreatedBy());
        assertEquals(TrainingType.PRODUCT, saved.getTrainingType());
        assertTrue(saved.isSelfRegistration() && saved.isWaitlistEnabled() && !saved.isRegistrationForm(), "the defaults");
        assertTrue(store.notifications.isEmpty());
    }

    @Test
    @DisplayName("Only staff with Trainer access, and HR, can schedule sessions (FR-12-05)")
    void onlyTrainersAndHr() {
        ForbiddenRequestException e = assertThrows(ForbiddenRequestException.class, () -> sessions.schedule(launch(), asAisyah));
        assertEquals("Only staff with Trainer access, and HR, can schedule sessions.", e.getMessage());

        sessions.schedule(launch(), asHr);

        assertEquals(1, store.sessions.size());
        assertEquals(hr.getId(), store.sessions.values().iterator().next().getCreatedBy());
    }

    @Test
    @DisplayName("A session needs a title, a venue, seats, and times that end after they start and are still to come")
    void requiredDetails() {
        refused("Enter a title.", r -> r.setTitle("  "));
        refused("The session must end after it starts, on the same day.", r -> r.setEndTime(LocalTime.of(9, 0)));
        refused("A session can't start in the past.", r -> {
            r.setDate(today);
            r.setStartTime(LocalTime.of(9, 30));
        });
        refused("Enter the venue.", r -> r.setVenue(null));
        refused("Seats must be between 1 and 1,000.", r -> r.setCapacity(0));
        refused("The registration cut-off must be on or before the session date.",
                r -> r.setRegistrationCutoff(today.plusDays(9)));
        refused("The registration cut-off can't be in the past.", r -> r.setRegistrationCutoff(today.minusDays(1)));
        assertTrue(store.sessions.isEmpty());
    }

    @Test
    @DisplayName("Physical and Hybrid training needs a training type; online and sharing sessions have none (FR-17-01)")
    void trainingType() {
        refused("Choose the training type: Product Training, Skill-Based Training or Other.", r -> r.setTrainingType(null));

        TrainingSessionDto online = sessions.schedule(launch(r -> {
            r.setDelivery(TrainingDelivery.ONLINE);
            r.setVenue(null);
        }), asWeiMing);
        TrainingSessionDto sharing = sessions.schedule(launch(r -> {
            r.setKind(TrainingSessionKind.STAFF_SHARING);
            r.setDelivery(TrainingDelivery.HYBRID);
        }), asWeiMing);
        TrainingSessionDto hybrid = sessions.schedule(launch(r -> {
            r.setDelivery(TrainingDelivery.HYBRID);
            r.setTrainingType(TrainingType.OTHER);
        }), asWeiMing);

        assertNull(online.getTrainingType(), "online sessions don't count towards the training KPI");
        assertNull(online.getVenue(), "and need no venue");
        assertNull(sharing.getTrainingType(), "nor do staff sharing sessions");
        assertEquals(TrainingType.OTHER, hybrid.getTrainingType());
    }

    @Test
    @DisplayName("Sessions are run by staff with Trainer access or HR; an external trainer can run training, not sharing (FR-15-05)")
    void whoRunsIt() {
        refused("Nur Aisyah can't run sessions: only staff with Trainer access, and HR, can. "
                + "The Super Admin can give Trainer access.", r -> r.setTrainerId(aisyah.getId()));
        refused("Nur Aisyah can't present sessions: only staff with Trainer access, and HR, can. "
                + "The Super Admin can give Trainer access.", r -> {
            r.setKind(TrainingSessionKind.STAFF_SHARING);
            r.setTrainerId(aisyah.getId());
        });
        refused("A staff sharing session is presented by a staff member with Trainer access, not an external trainer.", r -> {
            r.setKind(TrainingSessionKind.STAFF_SHARING);
            r.setTrainerId(null);
            r.setTrainerName("Daikin Malaysia trainer");
        });
        refused("Choose who runs the session.", r -> r.setTrainerId(null));

        TrainingSessionDto external = sessions.schedule(launch(r -> {
            r.setTrainerId(null);
            r.setTrainerName("Daikin Malaysia trainer");
        }), asWeiMing);
        assertTrue(external.isExternalTrainer());
        assertEquals("Daikin Malaysia trainer", external.getTrainerName());
        assertEquals("Hana Yusof", sessions.schedule(launch(r -> r.setTrainerId(hr.getId())), asWeiMing).getTrainerName(),
                "HR can run sessions");

        TrainerAccessGrant grant = new TrainerAccessGrant();
        grant.setStaffId(aisyah.getId());
        grant.setEndDate(today.plusDays(30));
        store.grants.put(aisyah.getId(), grant);
        TrainingSessionDto sharing = sessions.schedule(launch(r -> {
            r.setKind(TrainingSessionKind.STAFF_SHARING);
            r.setTrainerId(aisyah.getId());
        }), asWeiMing);
        assertEquals("Nur Aisyah", sharing.getTrainerName(), "Trainer access from the Super Admin lets her present");
    }

    @Test
    @DisplayName("The audience is chosen by department, job role or name, and counts active staff only (FR-12-03)")
    void audience() {
        Staff gone = store.person("Lim Ah Kow", salesExecutive);
        gone.setAccountStatus(StaffAccountStatus.INACTIVE);

        TrainingSessionDto dto = sessions.schedule(launch(r -> {
            r.setDepartmentIds(List.of(10L));
            r.setRoleIds(List.of(21L));
            r.setStaffIds(List.of(hr.getId()));
        }), asWeiMing);

        assertEquals("Sales Department; Technician (Service Department); Hana Yusof", dto.getAudience());
        assertEquals(4, dto.getAudienceSize(), "Aisyah, Ravi, Farid and Hana; not Lim, whose account is inactive");
        assertEquals(List.of(10L), dto.getDepartmentIds());
        refused("A chosen department no longer exists. Choose the audience again.", r -> r.setDepartmentIds(List.of(99L)));
        refused("Someone chosen for the audience has left or their account is inactive. Choose the audience again.",
                r -> r.setStaffIds(List.of(gone.getId())));
    }

    @Test
    @DisplayName("A mandatory session whose audience outnumbers its seats warns the trainer and suggests more runs (FR-12-15)")
    void capacityWarning() {
        Consumer<TrainingSessionRequest> salesAndTechnicians = r -> {
            r.setMandatory(true);
            r.setDepartmentIds(List.of(10L));
            r.setRoleIds(List.of(21L));
            r.setCapacity(2);
        };

        TrainingSessionDto dto = sessions.schedule(launch(salesAndTechnicians), asWeiMing);
        TrainingAudienceCheckDto check = sessions.checkAudience(launch(salesAndTechnicians));

        assertEquals("This mandatory session is for 3 people but has 2 seats. Schedule another run for the other 1 person.",
                dto.getCapacityWarning());
        assertEquals(3, check.getAudienceSize());
        assertEquals(dto.getCapacityWarning(), check.getWarning());
        assertNull(sessions.checkAudience(launch(salesAndTechnicians.andThen(r -> r.setMandatory(false)))).getWarning(),
                "an optional session needn't seat everyone");
        assertEquals("This mandatory session is for 45 people but has 20 seats. Schedule 2 more runs for the other 25 people.",
                TrainingServiceImpl.capacityWarning(true, 45, 20));
    }

    @Test
    @DisplayName("The trainer list offers staff with Trainer access, and HR, by name")
    void trainers() {
        assertEquals(List.of("Hana Yusof", "Karim Abdullah", "Tan Wei Ming"),
                sessions.trainers().stream().map(TrainingTrainerDto::getName).toList());
    }

    @Test
    @DisplayName("The calendar lists a date range in time order; staff see only the sessions meant for them")
    void calendar() {
        Long launch = sessions.schedule(launch(), asWeiMing).getId();
        Long refresher = sessions.schedule(launch(r -> {
            r.setTitle("R32 Safety Refresher");
            r.setStartTime(LocalTime.of(8, 0));
            r.setEndTime(LocalTime.of(8, 45));
            r.setRoleIds(List.of(21L));
        }), asHr).getId();
        sessions.schedule(launch(r -> r.setDate(today.plusDays(40))), asWeiMing);

        assertEquals(List.of(refresher, launch), ids(sessions.calendar(today, today.plusDays(30), false, asKarim)),
                "trainers see every session in the range, earliest first");
        assertEquals(List.of(launch), ids(sessions.calendar(today, today.plusDays(30), false, asAisyah)),
                "the refresher is for technicians only");
        assertEquals("Show at most a year of sessions at a time.", assertThrows(BadRequestException.class,
                () -> sessions.calendar(today, today.plusDays(400), false, asHr)).getMessage());
    }

    @Test
    @DisplayName("A trainer changes only the sessions they scheduled; HR changes any")
    void whoManages() {
        Long id = sessions.schedule(launch(), asWeiMing).getId();

        assertFalse(sessions.get(id, asKarim).isCanManage());
        ForbiddenRequestException e = assertThrows(ForbiddenRequestException.class,
                () -> sessions.update(id, launch(r -> r.setTitle("Mine now")), asKarim));
        assertEquals("Only whoever scheduled this session, and HR, can change it.", e.getMessage());
        assertThrows(ForbiddenRequestException.class, () -> sessions.cancel(id, "Not mine", asKarim));
        assertThrows(ForbiddenRequestException.class, () -> sessions.delete(id, asKarim));

        assertEquals("Launch, second run", sessions.update(id, launch(r -> r.setTitle("Launch, second run")), asHr).getTitle());
    }

    @Test
    @DisplayName("Moving a session tells everyone registered, waitlisted or invited; a new title tells no one")
    void changesAreAnnounced() {
        Staff siti = store.person("Siti Aminah", salesExecutive);
        Long id = sessions.schedule(launch(), asWeiMing).getId();
        registration(id, aisyah, TrainingRegistrationStatus.REGISTERED);
        registration(id, siti, TrainingRegistrationStatus.WAITLISTED);
        registration(id, ravi, TrainingRegistrationStatus.CANCELLED);
        invite(id, farid);

        sessions.update(id, launch(r -> r.setTitle("Inverter Series 2026 Launch, updated")), asWeiMing);
        assertTrue(store.notifications.isEmpty(), "only the title changed");

        sessions.update(id, launch(r -> {
            r.setDate(today.plusDays(9));
            r.setStartTime(LocalTime.of(14, 0));
            r.setEndTime(LocalTime.of(17, 0));
        }), asWeiMing);

        assertEquals(List.of(aisyah.getId(), siti.getId(), farid.getId()), told());
        SopTestStore.Sent sent = store.notifications.get(0);
        assertEquals(NotificationType.SESSION_CHANGED, sent.type());
        assertEquals("Session changed: Inverter Series 2026 Launch", sent.title());
        assertEquals("\"Inverter Series 2026 Launch\" is now on Wed 21 Oct 2026, 14:00 to 17:00, at Conference Hall B, HQ.",
                sent.body());
        assertEquals("/training/calendar?session=" + id, sent.link());
    }

    @Test
    @DisplayName("Seats can't drop below the people who have one, counting a seat held for an offer")
    void seatsNotBelowBooked() {
        Long id = sessions.schedule(launch(), asWeiMing).getId();
        registration(id, aisyah, TrainingRegistrationStatus.REGISTERED);
        registration(id, ravi, TrainingRegistrationStatus.OFFERED);
        registration(id, farid, TrainingRegistrationStatus.WAITLISTED);

        TrainingSessionDto dto = sessions.get(id, asWeiMing);
        assertEquals(2, dto.getBooked());
        assertEquals(18, dto.getSeatsLeft());
        assertEquals(1, dto.getWaitlisted());
        assertEquals("2 people already have a seat, so the session needs at least 2 seats.", assertThrows(
                BadRequestException.class, () -> sessions.update(id, launch(r -> r.setCapacity(1)), asWeiMing)).getMessage());
        assertEquals(2, sessions.update(id, launch(r -> r.setCapacity(2)), asWeiMing).getCapacity());
    }

    @Test
    @DisplayName("Once a session starts, only its title, description, trainer and training type can change")
    void lockedOnceStarted() {
        Long id = sessions.schedule(launch(), asWeiMing).getId();
        at(today.plusDays(8), 9, 30);

        BadRequestException e = assertThrows(BadRequestException.class, () -> sessions.update(id, launch(r -> {
            r.setVenue("Training Room A");
            r.setCapacity(25);
        }), asWeiMing));
        assertEquals("This session has started, so its venue and seats can't change.", e.getMessage());

        TrainingSessionDto dto = sessions.update(id, launch(r -> {
            r.setTitle("Inverter Series 2026 Launch, morning run");
            r.setDescription("Specs and selling points.");
            r.setTrainerId(karim.getId());
            r.setTrainingType(TrainingType.SKILL_BASED);
        }), asWeiMing);
        assertEquals("Karim Abdullah", dto.getTrainerName());
        assertEquals(TrainingType.SKILL_BASED, dto.getTrainingType());
        assertEquals(TrainingSessionTiming.IN_PROGRESS, dto.getTiming());
    }

    @Test
    @DisplayName("The training type can change only until attendance is recorded (FR-17-01)")
    void trainingTypeUntilAttendance() {
        Long id = sessions.schedule(launch(), asWeiMing).getId();
        at(today.plusDays(8), 9, 30);
        attended(id, aisyah);

        assertEquals("Attendance has been recorded for this session, so its training type can't change.", assertThrows(
                BadRequestException.class,
                () -> sessions.update(id, launch(r -> r.setTrainingType(TrainingType.SKILL_BASED)), asWeiMing)).getMessage());
        assertEquals("Launch notes", sessions.update(id, launch(r -> r.setDescription("Launch notes")), asWeiMing)
                .getDescription(), "the description still can");
    }

    @Test
    @DisplayName("A session can be deleted only while nobody has registered or been invited (FR-12-06)")
    void deleteRules() {
        Long empty = sessions.schedule(launch(), asWeiMing).getId();
        Long registered = sessions.schedule(launch(), asWeiMing).getId();
        Long invited = sessions.schedule(launch(), asWeiMing).getId();
        registration(registered, aisyah, TrainingRegistrationStatus.CANCELLED);
        invite(invited, farid);

        sessions.delete(empty, asWeiMing);

        assertTrue(store.sessions.get(empty).isDeleted());
        assertEquals("This session no longer exists.",
                assertThrows(BadRequestException.class, () -> sessions.get(empty, asWeiMing)).getMessage());
        String refusal = "Staff have registered for or been invited to this session, so it can't be deleted. "
                + "Cancel it instead, and they are told.";
        assertEquals(refusal, assertThrows(BadRequestException.class, () -> sessions.delete(registered, asWeiMing)).getMessage(),
                "even when the registration was cancelled since");
        assertEquals(refusal, assertThrows(BadRequestException.class, () -> sessions.delete(invited, asWeiMing)).getMessage());
        assertFalse(sessions.get(registered, asWeiMing).isCanDelete());
        TrainingAuditLog logged = store.auditLog.values().iterator().next();
        assertEquals(TrainingAuditAction.SESSION_DELETED.name(), logged.getAction());
        assertEquals("\"Inverter Series 2026 Launch\" on Tue 20 Oct 2026, 09:00 to 12:00", logged.getDetail());
    }

    @Test
    @DisplayName("A session with attendance recorded can only be archived (FR-12-06)")
    void attendedSessionsAreArchived() {
        Long id = sessions.schedule(launch(), asWeiMing).getId();
        at(today.plusDays(8), 12, 30);
        attended(id, aisyah);

        assertEquals("Attendance has been recorded for this session, so it can only be archived.",
                assertThrows(BadRequestException.class, () -> sessions.delete(id, asWeiMing)).getMessage());
        assertEquals("This session has already started, so it can't be cancelled.",
                assertThrows(BadRequestException.class, () -> sessions.cancel(id, "Too late", asWeiMing)).getMessage());
        assertEquals(TrainingSessionStatus.ARCHIVED, sessions.archive(id, asWeiMing).getStatus());
    }

    @Test
    @DisplayName("Cancelling before the start tells everyone registered, waitlisted or invited why (FR-12-06)")
    void cancel() {
        Staff siti = store.person("Siti Aminah", salesExecutive);
        Long id = sessions.schedule(launch(), asWeiMing).getId();
        registration(id, aisyah, TrainingRegistrationStatus.REGISTERED);
        registration(id, siti, TrainingRegistrationStatus.WAITLISTED);
        registration(id, ravi, TrainingRegistrationStatus.CANCELLED);
        invite(id, farid);

        assertEquals("Give a reason. It goes in the notice to everyone registered or invited.",
                assertThrows(BadRequestException.class, () -> sessions.cancel(id, " ", asWeiMing)).getMessage());
        TrainingSessionDto dto = sessions.cancel(id, "The trainer is unwell.", asWeiMing);

        assertEquals(TrainingSessionStatus.CANCELLED, dto.getStatus());
        assertEquals("The trainer is unwell.", dto.getCancelReason());
        assertEquals(List.of(aisyah.getId(), siti.getId(), farid.getId()), told());
        SopTestStore.Sent sent = store.notifications.get(0);
        assertEquals(NotificationType.SESSION_CANCELLED, sent.type());
        assertEquals("Session cancelled: Inverter Series 2026 Launch", sent.title());
        assertEquals("\"Inverter Series 2026 Launch\" on Tue 20 Oct 2026, 09:00 to 12:00 is cancelled. "
                + "Reason: The trainer is unwell.", sent.body());
        TrainingAuditLog logged = store.auditLog.values().iterator().next();
        assertEquals(TrainingAuditAction.SESSION_CANCELLED.name(), logged.getAction());
        assertEquals("The trainer is unwell.", logged.getReason());
        assertEquals("This session is already cancelled.",
                assertThrows(BadRequestException.class, () -> sessions.cancel(id, "Again", asWeiMing)).getMessage());
        assertEquals("This session was cancelled, so it can't be changed.",
                assertThrows(BadRequestException.class, () -> sessions.update(id, launch(), asWeiMing)).getMessage());
    }

    @Test
    @DisplayName("Staff still see a cancelled session they registered for; others no longer do")
    void cancelledStaysWithItsRegistrants() {
        Long id = sessions.schedule(launch(), asWeiMing).getId();
        registration(id, aisyah, TrainingRegistrationStatus.REGISTERED);
        sessions.cancel(id, "The trainer is unwell.", asWeiMing);
        TrainingViewer asRavi = new TrainingViewer(ravi.getId(), false, false, false);

        assertEquals(TrainingSessionStatus.CANCELLED, sessions.get(id, asAisyah).getStatus());
        assertTrue(sessions.calendar(today, today.plusDays(30), false, asRavi).isEmpty());
        assertThrows(ForbiddenRequestException.class, () -> sessions.get(id, asRavi));
    }

    @Test
    @DisplayName("A session is archived once it has ended or was cancelled, leaves the calendar, and can be restored")
    void archiveAndRestore() {
        Long held = sessions.schedule(launch(), asWeiMing).getId();
        Long cancelled = sessions.schedule(launch(), asWeiMing).getId();
        sessions.cancel(cancelled, "Moved to next month.", asWeiMing);

        assertEquals("Only a session that has ended, or was cancelled, can be archived. If it won't go ahead, cancel it.",
                assertThrows(BadRequestException.class, () -> sessions.archive(held, asWeiMing)).getMessage());
        at(today.plusDays(8), 12, 0);
        sessions.archive(held, asWeiMing);
        sessions.archive(cancelled, asWeiMing);

        assertTrue(sessions.calendar(today, today.plusDays(30), false, asWeiMing).isEmpty());
        assertEquals(List.of(held, cancelled), ids(sessions.calendar(today, today.plusDays(30), true, asWeiMing)));
        assertEquals(TrainingSessionStatus.SCHEDULED, sessions.restore(held, asHr).getStatus());
        assertEquals(TrainingSessionStatus.CANCELLED, sessions.restore(cancelled, asHr).getStatus(), "back as it was");
        assertEquals("This session isn't archived.",
                assertThrows(BadRequestException.class, () -> sessions.restore(held, asHr)).getMessage());
    }

    @Test
    @DisplayName("Whether a session is upcoming, in progress or over follows the Malaysia clock")
    void timing() {
        Long id = sessions.schedule(launch(), asWeiMing).getId();
        LocalDate day = today.plusDays(8);

        at(day, 8, 59);
        assertEquals(TrainingSessionTiming.UPCOMING, sessions.get(id, asWeiMing).getTiming());
        at(day, 9, 0);
        assertEquals(TrainingSessionTiming.IN_PROGRESS, sessions.get(id, asWeiMing).getTiming());
        at(day, 12, 0);
        assertEquals(TrainingSessionTiming.ENDED, sessions.get(id, asWeiMing).getTiming());
    }
}
