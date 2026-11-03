package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.TrainingAttendanceRequest;
import com.tbm.careerpathlearning.dto.TrainingRegistrationRequest;
import com.tbm.careerpathlearning.dto.TrainingSessionDto;
import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.dto.TrainingSharingBoardDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.enums.TrainingAttendanceStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAuditLog;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.function.Consumer;

import static com.tbm.careerpathlearning.enums.TrainingAttendanceStatus.ABSENT;
import static com.tbm.careerpathlearning.enums.TrainingAttendanceStatus.ATTENDED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Staff sharing sessions: the Staff Sharing page's figures, presenters and sessions, and the reminder to share what was
 * learned at a Product Training, with the request for Trainer access (FR-15-01 to FR-15-03, FR-15-06, decisions A to D
 * of 29 Oct 2026).
 */
class TrainingSharingTest {

    private SopTestStore store;
    private TrainingServiceImpl sessions;
    private TrainingRegistrationServiceImpl registrations;
    private TrainingAttendanceServiceImpl attendance;
    private TrainingSharingServiceImpl sharing;
    private OrgChart service;
    private Staff weiMing, karim, aisyah, ravi, siti, farid, admin;
    private TrainingViewer asHr, asWeiMing, asKarim, asAisyah, asRavi, asSiti, asFarid;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        sessions = store.wire(new TrainingServiceImpl());
        registrations = (TrainingRegistrationServiceImpl) store.sessionRegistrations();
        attendance = store.wire(new TrainingAttendanceServiceImpl());
        sharing = store.wire(new TrainingSharingServiceImpl());
        OrgChart sales = store.department(10L, "Sales Department");
        service = store.department(11L, "Service Department");
        OrgChart people = store.department(12L, "Human Resources");
        Role salesExecutive = store.role(20L, "Sales Executive", sales);
        Role technician = store.role(21L, "Technician", service);
        Role trainer = store.role(22L, "Trainer", people);
        Role hrExecutive = store.role(23L, "HR Executive", people);
        Role superAdmin = store.role(24L, "Super Admin", people);
        store.permit(trainer, AuthorityName.CAN_MANAGE_TRAINING);
        store.permit(hrExecutive, AuthorityName.CAN_ASSIGN_TRAINING);
        store.permit(superAdmin, AuthorityName.CAN_MANAGE_ACCESS_CONTROL);
        Staff hr = store.person("Hana Yusof", hrExecutive);
        weiMing = store.person("Tan Wei Ming", trainer);
        karim = store.person("Karim Abdullah", trainer);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        ravi = store.person("Ravi Kumar", salesExecutive);
        siti = store.person("Siti Aminah", salesExecutive);
        farid = store.person("Ahmad Farid", technician);
        admin = store.person("Lee Mei Hua", superAdmin);
        asHr = new TrainingViewer(hr.getId(), false, true, false);
        asWeiMing = new TrainingViewer(weiMing.getId(), true, false, false);
        asKarim = new TrainingViewer(karim.getId(), true, false, false);
        asAisyah = new TrainingViewer(aisyah.getId(), false, false, false);
        asRavi = new TrainingViewer(ravi.getId(), false, false, false);
        asSiti = new TrainingViewer(siti.getId(), false, false, false);
        asFarid = new TrainingViewer(farid.getId(), false, false, false);
        today = store.today(); // Monday 12 Oct 2026, 10:00 in Malaysia
    }

    /** A staff sharing session for everyone, an hour from 09:00 on the given day, presented and scheduled by the trainer. */
    private Long sharingSession(TrainingViewer by, Staff presenter, int inDays, Consumer<TrainingSessionRequest> change) {
        TrainingSessionRequest r = new TrainingSessionRequest();
        r.setTitle("Sharing by " + presenter.getName());
        r.setKind(TrainingSessionKind.STAFF_SHARING);
        r.setDelivery(TrainingDelivery.PHYSICAL);
        r.setDate(today.plusDays(inDays));
        r.setStartTime(LocalTime.of(9, 0));
        r.setEndTime(LocalTime.of(10, 0));
        r.setVenue("Training Room 2, HQ");
        r.setCapacity(20);
        r.setTrainerId(presenter.getId());
        change.accept(r);
        return sessions.schedule(r, by).getId();
    }

    /** A Physical training of the type given, 09:00 to 12:00 on the given day, run by Tan Wei Ming. */
    private Long training(TrainingType type, int inDays) {
        TrainingSessionRequest r = new TrainingSessionRequest();
        r.setTitle("Inverter Series 2026 Launch");
        r.setKind(TrainingSessionKind.TRAINING);
        r.setDelivery(TrainingDelivery.PHYSICAL);
        r.setTrainingType(type);
        r.setDate(today.plusDays(inDays));
        r.setStartTime(LocalTime.of(9, 0));
        r.setEndTime(LocalTime.of(12, 0));
        r.setVenue("Conference Hall B, HQ");
        r.setCapacity(20);
        r.setTrainerId(weiMing.getId());
        return sessions.schedule(r, asWeiMing).getId();
    }

    private void register(Long id, TrainingViewer... viewers) {
        for (TrainingViewer viewer : viewers) {
            registrations.register(id, new TrainingRegistrationRequest(), viewer);
        }
    }

    /** Marks the people as given (Staff, status, Staff, status...) as the session's trainer would, then submits. */
    private void held(Long id, TrainingViewer by, Object... marks) {
        LocalDateTime end = store.sessions.get(id).endsAt();
        if (LocalDateTime.now(store.clock).isBefore(end)) at(end);
        TrainingAttendanceRequest r = new TrainingAttendanceRequest();
        for (int i = 0; i < marks.length; i += 2) {
            TrainingAttendanceRequest.Entry e = new TrainingAttendanceRequest.Entry();
            e.setStaffId(((Staff) marks[i]).getId());
            e.setStatus((TrainingAttendanceStatus) marks[i + 1]);
            if (marks[i] == aisyah && marks[i + 1] == ATTENDED) e.setQuizScore(80);
            r.getEntries().add(e);
        }
        attendance.record(id, r, by);
        attendance.submit(id, null, by);
    }

    private void at(LocalDateTime when) {
        store.clock.set(when.atZone(ClockConfig.ZONE).toInstant());
    }

    private List<SopTestStore.Sent> sent(NotificationType type) {
        return store.notifications.stream().filter(n -> n.type() == type).toList();
    }

    private static void refused(String message, Executable action) {
        assertEquals(message, assertThrows(BadRequestException.class, action).getMessage());
    }

    @Test
    @DisplayName("The page counts only submitted sessions, and staff reached apart from attendances, never over headcount")
    void figures() {
        Long first = sharingSession(asWeiMing, weiMing, 8, r -> { });
        Long second = sharingSession(asKarim, karim, 9, r -> { });
        Long notSubmitted = sharingSession(asKarim, karim, 10, r -> { });
        register(first, asAisyah, asRavi, asSiti);
        register(second, asAisyah, asFarid);
        register(notSubmitted, asRavi);
        held(first, asWeiMing, aisyah, ATTENDED, ravi, ATTENDED, siti, ABSENT);
        held(second, asKarim, aisyah, ATTENDED, farid, ATTENDED);
        at(store.sessions.get(notSubmitted).endsAt());
        farid.setAccountStatus(StaffAccountStatus.INACTIVE); // left: still counted for Karim, not in the reach

        TrainingSharingBoardDto board = sharing.board(null, asAisyah);

        assertEquals(2026, board.getYear());
        assertEquals(List.of(2026), board.getYears());
        assertEquals(2, board.getSessionsHeld());
        assertEquals(2, board.getPresenters());
        assertEquals(4, board.getTotalAttendances());
        assertEquals(2, board.getStaffReached()); // Nur Aisyah twice, Ravi Kumar; Ahmad Farid has left
        assertEquals(7, board.getHeadcount());
        assertEquals(29, board.getReachPercent());
        assertEquals(2.0, board.getAverageAttendance());
        assertEquals(80, board.getAverageQuizScore());

        assertEquals(List.of("Karim Abdullah", "Tan Wei Ming"),
                board.getStaffWhoShared().stream().map(TrainingSharingBoardDto.Presenter::getName).toList());
        TrainingSharingBoardDto.Presenter k = board.getStaffWhoShared().get(0);
        assertEquals(1, k.getSessions());
        assertEquals(2, k.getAttendances());
        assertEquals(2, k.getStaffReached());
        assertEquals("Human Resources", k.getDepartmentName());
        assertEquals(today.plusDays(9), k.getLastShared());
        assertFalse(k.isLeftCompany());

        assertEquals(List.of(second, first), board.getSessions().stream().map(TrainingSharingBoardDto.Held::getId).toList());
        TrainingSharingBoardDto.Held h = board.getSessions().get(1);
        assertEquals("Tan Wei Ming", h.getPresenterName());
        assertEquals(2, h.getAttended());
        assertEquals(3, h.getOnList());

        TrainingSharingBoardDto earlier = sharing.board(2025, asAisyah);
        assertEquals(0, earlier.getSessionsHeld());
        assertNull(earlier.getAverageAttendance());
        assertTrue(earlier.getStaffWhoShared().isEmpty());
        refused("Choose a year from the list.", () -> sharing.board(1999, asAisyah));
    }

    @Test
    @DisplayName("A session's audience decides who sees it on the page; the figures and presenters are company-wide (decision A)")
    void audienceDecides() {
        Long forService = sharingSession(asKarim, karim, 8, r -> r.setDepartmentIds(List.of(service.getId())));
        Long upcomingForService = sharingSession(asKarim, karim, 15, r -> r.setDepartmentIds(List.of(service.getId())));
        Long upcomingForAll = sharingSession(asWeiMing, weiMing, 16, r -> { });
        Long training = training(TrainingType.OTHER, 8);
        register(forService, asFarid);
        held(forService, asKarim, farid, ATTENDED);

        TrainingSharingBoardDto forAisyah = sharing.board(null, asAisyah);
        assertEquals(1, forAisyah.getSessionsHeld());
        assertEquals(List.of("Karim Abdullah"), forAisyah.getStaffWhoShared().stream().map(p -> p.getName()).toList());
        assertTrue(forAisyah.getSessions().isEmpty());
        assertEquals(List.of(upcomingForAll), forAisyah.getUpcoming().stream().map(TrainingSessionDto::getId).toList());

        TrainingSharingBoardDto forFarid = sharing.board(null, asFarid);
        assertEquals(List.of(forService), forFarid.getSessions().stream().map(TrainingSharingBoardDto.Held::getId).toList());
        assertEquals(List.of(upcomingForService, upcomingForAll),
                forFarid.getUpcoming().stream().map(TrainingSessionDto::getId).toList());
        assertEquals("Karim Abdullah", forFarid.getUpcoming().get(0).getTrainerName());

        TrainingSharingBoardDto forTrainer = sharing.board(null, asWeiMing);
        assertEquals(1, forTrainer.getSessions().size());
        assertEquals(2, forTrainer.getUpcoming().size()); // training sessions aren't listed
        assertFalse(forTrainer.getUpcoming().stream().anyMatch(s -> s.getId().equals(training)));
    }

    @Test
    @DisplayName("After a Product Training, those who attended are asked once to share it; HR's later Attended asks too (FR-15-06)")
    void sharingReminder() {
        Long id = training(TrainingType.PRODUCT, 8);
        Long skills = training(TrainingType.SKILL_BASED, 9);
        register(id, asAisyah, asRavi, asKarim);
        register(skills, asSiti);
        at(store.sessions.get(id).endsAt());
        TrainingAttendanceRequest marks = new TrainingAttendanceRequest();
        for (Object[] m : new Object[][]{{aisyah, ATTENDED}, {ravi, ABSENT}, {karim, ATTENDED}}) {
            TrainingAttendanceRequest.Entry e = new TrainingAttendanceRequest.Entry();
            e.setStaffId(((Staff) m[0]).getId());
            e.setStatus((TrainingAttendanceStatus) m[1]);
            marks.getEntries().add(e);
        }
        attendance.record(id, marks, asWeiMing);
        assertTrue(sent(NotificationType.SHARING_SUGGESTED).isEmpty(), "not before it is submitted");

        attendance.submit(id, null, asWeiMing);

        List<SopTestStore.Sent> asked = sent(NotificationType.SHARING_SUGGESTED);
        assertEquals(List.of(aisyah.getId(), karim.getId()), asked.stream().map(SopTestStore.Sent::recipient).toList());
        assertEquals("Share what you learned", asked.get(0).title());
        assertEquals("You attended \"Inverter Series 2026 Launch\" on Tue 20 Oct 2026. To pass it on in a staff sharing "
                + "session, open the session and ask for Trainer access.", asked.get(0).body());
        assertEquals("/training/calendar?session=" + id, asked.get(0).link());
        assertEquals("You attended \"Inverter Series 2026 Launch\" on Tue 20 Oct 2026. Pass it on to colleagues: schedule a "
                + "staff sharing session from the Training Calendar.", asked.get(1).body());
        assertEquals("REQUEST", sessions.get(id, asAisyah).getSharingInvite());
        assertEquals("CAN_PRESENT", sessions.get(id, asKarim).getSharingInvite());
        assertNull(sessions.get(id, asRavi).getSharingInvite());

        TrainingAttendanceRequest late = new TrainingAttendanceRequest();
        TrainingAttendanceRequest.Entry e = new TrainingAttendanceRequest.Entry();
        e.setStaffId(ravi.getId());
        e.setStatus(ATTENDED);
        late.getEntries().add(e);
        late.setReason("Signed the paper sheet");
        attendance.record(id, late, asHr);
        assertEquals(List.of(aisyah.getId(), karim.getId(), ravi.getId()),
                sent(NotificationType.SHARING_SUGGESTED).stream().map(SopTestStore.Sent::recipient).toList());

        held(skills, asWeiMing, siti, ATTENDED);
        assertEquals(3, sent(NotificationType.SHARING_SUGGESTED).size()); // only Product Training asks
        assertNull(sessions.get(skills, asSiti).getSharingInvite());
    }

    @Test
    @DisplayName("Someone who attended can ask the Super Admin for Trainer access, once per session, logged (FR-15-06)")
    void requestTrainerAccess() {
        Long id = training(TrainingType.PRODUCT, 8);
        Long skills = training(TrainingType.SKILL_BASED, 9);
        register(id, asAisyah, asRavi, asKarim);
        register(skills, asAisyah);
        String fromSession = "Trainer access can be asked for from a Product Training session you attended, once its "
                + "attendance is submitted.";
        refused(fromSession, () -> sharing.requestTrainerAccess(id, asAisyah));
        held(id, asWeiMing, aisyah, ATTENDED, ravi, ABSENT, karim, ATTENDED);
        held(skills, asWeiMing, aisyah, ATTENDED);

        TrainingSessionDto after = sharing.requestTrainerAccess(id, asAisyah);

        assertEquals("REQUESTED", after.getSharingInvite());
        assertEquals(LocalDateTime.now(store.clock), after.getTrainerAccessRequestedAt());
        SopTestStore.Sent told = sent(NotificationType.TRAINER_ACCESS_REQUESTED).get(0);
        assertEquals(admin.getId(), told.recipient());
        assertEquals("Trainer access requested: Nur Aisyah", told.title());
        assertEquals("Nur Aisyah attended \"Inverter Series 2026 Launch\" on Tue 20 Oct 2026 and would like to present a "
                + "staff sharing session. You can give Trainer access, with an end date, on the Trainer Access page.", told.body());
        assertEquals("/sop/trainer-access?search=Nur%20Aisyah", told.link());
        TrainingAuditLog logged = store.auditLog.values().stream()
                .filter(a -> a.getAction().equals(TrainingAuditAction.TRAINER_ACCESS_REQUESTED.name())).findFirst().orElseThrow();
        assertEquals(aisyah.getId(), logged.getActorId());
        assertEquals("After \"Inverter Series 2026 Launch\" on Tue 20 Oct 2026", logged.getDetail());

        refused("You've already asked. The Super Admin has been told.", () -> sharing.requestTrainerAccess(id, asAisyah));
        refused("Only staff who attended this session can ask for Trainer access from it.",
                () -> sharing.requestTrainerAccess(id, asRavi));
        refused("You can already present: schedule a staff sharing session from the Training Calendar.",
                () -> sharing.requestTrainerAccess(id, asKarim));
        refused(fromSession, () -> sharing.requestTrainerAccess(skills, asAisyah));
        assertEquals(1, store.accessRequests.size());
        assertEquals(1, sent(NotificationType.TRAINER_ACCESS_REQUESTED).size());
    }
}
