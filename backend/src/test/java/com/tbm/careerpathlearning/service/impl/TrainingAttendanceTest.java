package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.TrainingAttendanceRequest;
import com.tbm.careerpathlearning.dto.TrainingAttendanceSheetDto;
import com.tbm.careerpathlearning.dto.TrainingRecordDto;
import com.tbm.careerpathlearning.dto.TrainingRegistrationRequest;
import com.tbm.careerpathlearning.dto.TrainingSessionDto;
import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.enums.TrainingAttendanceStatus;
import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.enums.TrainingType;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAuditLog;
import com.tbm.careerpathlearning.service.TrainingAttendanceService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static com.tbm.careerpathlearning.enums.TrainingAttendanceStatus.ABSENT;
import static com.tbm.careerpathlearning.enums.TrainingAttendanceStatus.ATTENDED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Recording attendance at sessions: who records it and when, walk-ins, submitting it, HR's logged changes afterwards,
 * evidence files, and the sessions awaiting attendance (FR-14-01 to FR-14-04, FR-14-08 to FR-14-10, decisions A to D
 * of 28 Oct 2026).
 */
class TrainingAttendanceTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 4, 5, 6};
    private static final byte[] PDF = "%PDF-1.7 evidence".getBytes();

    private SopTestStore store;
    private TrainingServiceImpl sessions;
    private TrainingRegistrationServiceImpl registrations;
    private TrainingAttendanceServiceImpl attendance;
    private Staff hr, weiMing, karim, aisyah, ravi, siti, farid;
    private TrainingViewer asHr, asWeiMing, asKarim, asAisyah, asRavi, asSiti, asSuperAdmin;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        sessions = store.wire(new TrainingServiceImpl());
        registrations = (TrainingRegistrationServiceImpl) store.sessionRegistrations();
        attendance = store.wire(new TrainingAttendanceServiceImpl());
        OrgChart sales = store.department(10L, "Sales Department");
        OrgChart service = store.department(11L, "Service Department");
        OrgChart people = store.department(12L, "Human Resources");
        Role salesExecutive = store.role(20L, "Sales Executive", sales);
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
        siti = store.person("Siti Aminah", salesExecutive);
        farid = store.person("Ahmad Farid", technician);
        asHr = new TrainingViewer(hr.getId(), false, true, false);
        asWeiMing = new TrainingViewer(weiMing.getId(), true, false, false);
        asKarim = new TrainingViewer(karim.getId(), true, false, false);
        asAisyah = new TrainingViewer(aisyah.getId(), false, false, false);
        asRavi = new TrainingViewer(ravi.getId(), false, false, false);
        asSiti = new TrainingViewer(siti.getId(), false, false, false);
        asSuperAdmin = new TrainingViewer(UUID.randomUUID(), false, false, true);
        today = store.today(); // Monday 12 Oct 2026, 10:00 in Malaysia
    }

    /** A Physical product training for 20, Tuesday 20 Oct 2026, 09:00 to 12:00, scheduled and run by Tan Wei Ming. */
    private Long session(Consumer<TrainingSessionRequest> change) {
        return session(asWeiMing, change);
    }

    private Long session(TrainingViewer by, Consumer<TrainingSessionRequest> change) {
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
        change.accept(r);
        return sessions.schedule(r, by).getId();
    }

    private void register(Long id, TrainingViewer... viewers) {
        for (TrainingViewer viewer : viewers) {
            registrations.register(id, new TrainingRegistrationRequest(), viewer);
        }
    }

    private static TrainingAttendanceRequest.Entry entry(Staff s, TrainingAttendanceStatus status, Integer score) {
        TrainingAttendanceRequest.Entry e = new TrainingAttendanceRequest.Entry();
        e.setStaffId(s.getId());
        e.setStatus(status);
        e.setQuizScore(score);
        return e;
    }

    private TrainingAttendanceSheetDto record(Long id, TrainingViewer viewer, TrainingAttendanceRequest.Entry... entries) {
        return record(id, viewer, null, entries);
    }

    private TrainingAttendanceSheetDto record(Long id, TrainingViewer viewer, String reason,
                                              TrainingAttendanceRequest.Entry... entries) {
        TrainingAttendanceRequest r = new TrainingAttendanceRequest();
        r.setEntries(List.of(entries));
        r.setReason(reason);
        return attendance.record(id, r, viewer);
    }

    private static TrainingAttendanceSheetDto.Row row(TrainingAttendanceSheetDto sheet, Staff s) {
        return sheet.getRows().stream().filter(r -> r.getStaffId().equals(s.getId())).findFirst().orElseThrow();
    }

    private static List<String> names(TrainingAttendanceSheetDto sheet) {
        return sheet.getRows().stream().map(TrainingAttendanceSheetDto.Row::getName).toList();
    }

    private static List<Long> ids(List<TrainingSessionDto> list) {
        return list.stream().map(TrainingSessionDto::getId).toList();
    }

    private TrainingAuditLog lastAudit() {
        return store.auditLog.values().stream().reduce((a, b) -> b).orElseThrow();
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private static void refused(String message, Executable action) {
        assertEquals(message, assertThrows(BadRequestException.class, action).getMessage());
    }

    private void at(LocalDate date, int hour, int minute) {
        store.clock.set(LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ClockConfig.ZONE).toInstant());
    }

    // --- who records, and when -----------------------------------------------------------------

    @Test
    @DisplayName("The trainer, whoever scheduled the session, and HR record attendance once it starts (FR-14-01)")
    void whoRecords() {
        Long id = session(r -> { });
        Long karims = session(r -> {
            r.setTrainerId(karim.getId());
            r.setStartTime(LocalTime.of(14, 0));
            r.setEndTime(LocalTime.of(16, 0));
        });
        register(id, asAisyah);
        register(karims, asAisyah);

        refused("Attendance can be recorded once the session starts, at Tue 20 Oct, 09:00.",
                () -> record(id, asWeiMing, entry(aisyah, ATTENDED, null)));
        at(today.plusDays(8), 9, 0);
        assertEquals("ATTENDED", row(record(id, asWeiMing, entry(aisyah, ATTENDED, null)), aisyah).getStatus());
        assertEquals("ABSENT", row(record(id, asHr, entry(aisyah, ABSENT, null)), aisyah).getStatus());
        assertThrows(ForbiddenRequestException.class, () -> record(id, asKarim, entry(aisyah, ATTENDED, null)));
        assertThrows(ForbiddenRequestException.class, () -> record(id, asAisyah, entry(aisyah, ATTENDED, null)));
        assertThrows(ForbiddenRequestException.class, () -> attendance.sheet(id, asAisyah));
        at(today.plusDays(8), 14, 0);
        record(karims, asKarim, entry(aisyah, ATTENDED, null)); // runs it
        record(karims, asWeiMing, entry(aisyah, ABSENT, null)); // scheduled it

        TrainingAttendanceSheetDto looked = attendance.sheet(id, asSuperAdmin);
        assertEquals(1, looked.getAbsent());
        assertFalse(looked.isCanRecord());
        assertThrows(ForbiddenRequestException.class, () -> record(id, asSuperAdmin, entry(aisyah, ATTENDED, null)));
        assertTrue(sessions.get(id, asWeiMing).isCanRecordAttendance());
        assertFalse(sessions.get(id, asKarim).isCanRecordAttendance());
        assertFalse(sessions.get(id, asAisyah).isCanRecordAttendance());
    }

    @Test
    @DisplayName("The sheet lists everyone registered by name, Not recorded until marked; the waitlist isn't on it (FR-14-04)")
    void sheet() {
        Long id = session(r -> r.setCapacity(2));
        register(id, asRavi, asAisyah, asSiti); // Siti Aminah is waitlisted
        at(today.plusDays(8), 9, 30);

        TrainingAttendanceSheetDto sheet = attendance.sheet(id, asWeiMing);

        assertEquals(List.of("Nur Aisyah", "Ravi Kumar"), names(sheet));
        assertEquals("NOT_RECORDED", row(sheet, aisyah).getStatus());
        assertEquals("Sales Executive", row(sheet, aisyah).getRoleName());
        assertEquals("Sales Department", row(sheet, aisyah).getDepartmentName());
        assertEquals(2, sheet.getNotRecorded());
        assertTrue(sheet.isCanRecord());
        assertFalse(sheet.isHrOnly());
        assertFalse(sheet.isCanSubmit());
        assertEquals(LocalDateTime.of(today.plusDays(8), LocalTime.of(9, 0)), sheet.getOpensAt());
        assertEquals(LocalDateTime.of(today.plusDays(15), LocalTime.of(12, 0)), sheet.getTrainerUntil());
    }

    @Test
    @DisplayName("Attended with a quiz score, or Absent, or cleared, each with who recorded it and when (FR-14-02, FR-14-03)")
    void marks() {
        Long id = session(r -> { });
        register(id, asAisyah, asRavi);
        at(today.plusDays(8), 11, 45);

        TrainingAttendanceSheetDto sheet = record(id, asWeiMing, entry(aisyah, ATTENDED, 85), entry(ravi, ABSENT, null));

        TrainingAttendanceSheetDto.Row marked = row(sheet, aisyah);
        assertEquals("ATTENDED", marked.getStatus());
        assertEquals(85, marked.getQuizScore());
        assertEquals("MANUAL", marked.getMethod());
        assertEquals("Tan Wei Ming", marked.getRecordedByName());
        assertEquals(LocalDateTime.of(today.plusDays(8), LocalTime.of(11, 45)), marked.getRecordedAt());
        assertEquals("ABSENT", row(sheet, ravi).getStatus());
        assertEquals(1, sheet.getAttended());
        assertEquals(1, sheet.getAbsent());

        refused("A quiz score goes with Attended only.", () -> record(id, asWeiMing, entry(ravi, ABSENT, 50)));
        refused("Quiz scores are from 0 to 100.", () -> record(id, asWeiMing, entry(aisyah, ATTENDED, 101)));
        refused("Siti Aminah isn't on this session. Add them as a walk-in first.",
                () -> record(id, asWeiMing, entry(siti, ATTENDED, null)));
        // A request with a mistake changes nothing, even its good entries.
        refused("Nur Aisyah is marked twice. Mark each person once.",
                () -> record(id, asWeiMing, entry(ravi, ATTENDED, null), entry(aisyah, ATTENDED, null), entry(aisyah, ABSENT, null)));
        assertEquals("ABSENT", row(attendance.sheet(id, asWeiMing), ravi).getStatus());

        sheet = record(id, asWeiMing, entry(ravi, null, null));
        assertEquals("NOT_RECORDED", row(sheet, ravi).getStatus());
        assertNull(row(sheet, ravi).getRecordedByName());
        assertTrue(store.auditLog.values().stream().noneMatch(e -> e.getAction().equals("ATTENDANCE_CHANGED")),
                "changes within the trainer's 7 days aren't logged");
    }

    @Test
    @DisplayName("Someone who came without registering is added as a walk-in, marked Attended (decision A)")
    void walkIns() {
        Long id = session(r -> { });
        register(id, asAisyah);
        at(today.plusDays(8), 9, 30);

        TrainingAttendanceSheetDto sheet = attendance.addWalkIn(id, farid.getId(), null, asWeiMing);

        assertEquals(List.of("Ahmad Farid", "Nur Aisyah"), names(sheet));
        assertEquals("ATTENDED", row(sheet, farid).getStatus());
        assertTrue(row(sheet, farid).isWalkIn());
        assertFalse(row(sheet, aisyah).isWalkIn());
        refused("Ahmad Farid is already on the attendance list.", () -> attendance.addWalkIn(id, farid.getId(), null, asWeiMing));
        refused("Nur Aisyah is already on the attendance list.", () -> attendance.addWalkIn(id, aisyah.getId(), null, asWeiMing));
        refused("Tan Wei Ming runs this session.", () -> attendance.addWalkIn(id, weiMing.getId(), null, asWeiMing));
        siti.setAccountStatus(StaffAccountStatus.INACTIVE);
        refused("Choose an active staff member.", () -> attendance.addWalkIn(id, siti.getId(), null, asWeiMing));

        // Clearing a walk-in's mark takes them off the list again.
        assertEquals(List.of("Nur Aisyah"), names(record(id, asWeiMing, entry(farid, null, null))));
    }

    // --- submitting ------------------------------------------------------------------------------

    @Test
    @DisplayName("Submit needs everyone marked; afterwards only HR changes it, giving a reason that is logged (FR-14-09, decision C)")
    void submit() {
        Long id = session(r -> { });
        register(id, asAisyah, asRavi, asSiti);
        at(today.plusDays(8), 12, 30);
        record(id, asWeiMing, entry(aisyah, ATTENDED, 90));

        refused("Mark everyone first: Ravi Kumar and Siti Aminah are still Not recorded.",
                () -> attendance.submit(id, null, asWeiMing));
        assertFalse(attendance.sheet(id, asWeiMing).isCanSubmit());
        record(id, asWeiMing, entry(ravi, ATTENDED, null), entry(siti, ABSENT, null));
        assertTrue(attendance.sheet(id, asWeiMing).isCanSubmit());

        TrainingAttendanceSheetDto sheet = attendance.submit(id, null, asWeiMing);

        assertTrue(sheet.isSubmitted());
        assertEquals("Tan Wei Ming", sheet.getSubmittedByName());
        assertEquals(LocalDateTime.of(today.plusDays(8), LocalTime.of(12, 30)), sheet.getSubmittedAt());
        assertFalse(sheet.isCanRecord());
        assertTrue(sheet.isHrOnly());
        assertFalse(sheet.isCanChangeWithReason());
        assertEquals("ATTENDANCE_SUBMITTED", lastAudit().getAction());
        assertEquals("2 attended, 1 absent", lastAudit().getDetail());
        refused("Attendance has already been submitted.", () -> attendance.submit(id, null, asHr));
        refused("Attendance has been submitted. Only HR can change it now.",
                () -> record(id, asWeiMing, entry(siti, ATTENDED, null)));
        refused("Attendance has been submitted. Only HR can change it now.",
                () -> attendance.addWalkIn(id, farid.getId(), null, asWeiMing));
        refused("Give a reason for the change. It is logged.", () -> record(id, asHr, entry(siti, ATTENDED, null)));
        assertTrue(attendance.sheet(id, asHr).isCanChangeWithReason());

        record(id, asHr, "Came late and signed the paper sheet.", entry(siti, ATTENDED, null));

        TrainingAuditLog changed = lastAudit();
        assertEquals("ATTENDANCE_CHANGED", changed.getAction());
        assertEquals(hr.getId(), changed.getActorId());
        assertEquals("Came late and signed the paper sheet.", changed.getReason());
        assertEquals("Siti Aminah: Absent to Attended", changed.getDetail());
        attendance.addWalkIn(id, farid.getId(), "Joined from the service centre.", asHr);
        assertEquals("Ahmad Farid: walk-in, Attended", lastAudit().getDetail());
    }

    @Test
    @DisplayName("A session reaches someone's training record once submitted, and HR's later change shows there too (FR-18-01)")
    void trainingRecordFollowsAttendance() {
        TrainingRecordServiceImpl records = store.wire(new TrainingRecordServiceImpl());
        Long id = session(r -> { });
        register(id, asAisyah, asSiti);
        at(today.plusDays(8), 12, 30);
        record(id, asWeiMing, entry(aisyah, ATTENDED, 90), entry(siti, ABSENT, null));

        assertTrue(records.record(siti.getId(), asSiti).getItems().isEmpty(), "not on the record until submitted");

        attendance.submit(id, null, asWeiMing);
        TrainingRecordDto absent = records.record(siti.getId(), asSiti);
        assertEquals("ABSENT", absent.getItems().get(0).getStatus());
        assertNull(absent.getItems().get(0).getHours());
        assertEquals(0, absent.getCompletedItems());
        assertEquals(0.0, absent.getSessionHours());

        record(id, asHr, "Came late and signed the paper sheet.", entry(siti, ATTENDED, 75));
        TrainingRecordDto attended = records.record(siti.getId(), asSiti);
        assertEquals(1, attended.getItems().size(), "the change updates the session's row, not a second one");
        assertEquals("ATTENDED", attended.getItems().get(0).getStatus());
        assertEquals(75, attended.getItems().get(0).getScore());
        assertEquals(3.0, attended.getItems().get(0).getHours());
        assertEquals(1, attended.getCompletedItems());
        assertEquals(3.0, attended.getSessionHours());
        assertEquals(3.0, records.record(aisyah.getId(), asHr).getSessionHours());
    }

    @Test
    @DisplayName("After 7 days the trainer can't record or submit; HR still can, with a reason (FR-14-01)")
    void trainersSevenDays() {
        Long id = session(r -> { });
        register(id, asAisyah);
        at(today.plusDays(15), 12, 0); // 7 days after it ended
        record(id, asWeiMing, entry(aisyah, ABSENT, null));
        at(today.plusDays(15), 12, 1);

        TrainingAttendanceSheetDto sheet = attendance.sheet(id, asWeiMing);
        assertFalse(sheet.isCanRecord());
        assertTrue(sheet.isHrOnly());
        assertFalse(sheet.isCanSubmit());
        refused("The 7 days for recording attendance have passed. Only HR can record it now.",
                () -> record(id, asWeiMing, entry(aisyah, ATTENDED, null)));
        refused("The 7 days for recording attendance have passed. Only HR can record it now.",
                () -> attendance.submit(id, null, asWeiMing));
        assertTrue(attendance.sheet(id, asHr).isCanSubmit());

        record(id, asHr, "Confirmed with the branch manager.", entry(aisyah, ATTENDED, null));
        attendance.submit(id, "Confirmed with the branch manager.", asHr);

        assertEquals("ATTENDANCE_SUBMITTED", lastAudit().getAction());
        assertEquals("Confirmed with the branch manager.", lastAudit().getReason());
    }

    @Test
    @DisplayName("Staff see their own attendance and quiz score once it is submitted (FR-14-04)")
    void ownAttendance() {
        Long id = session(r -> { });
        register(id, asAisyah, asRavi);
        at(today.plusDays(8), 12, 0);
        record(id, asWeiMing, entry(aisyah, ATTENDED, 75), entry(ravi, ABSENT, null));
        assertNull(sessions.get(id, asAisyah).getMyAttendance());

        attendance.submit(id, null, asWeiMing);

        TrainingSessionDto mine = sessions.get(id, asAisyah);
        assertEquals("ATTENDED", mine.getMyAttendance());
        assertEquals(75, mine.getMyQuizScore());
        assertEquals("ABSENT", sessions.get(id, asRavi).getMyAttendance());
        assertNotNull(mine.getAttendanceSubmittedAt());
    }

    @Test
    @DisplayName("A cancelled session has no attendance; an archived one is restored first; nobody on it means nothing to submit")
    void cancelledOrArchived() {
        Long cancelled = session(r -> { });
        Long empty = session(r -> {
            r.setStartTime(LocalTime.of(14, 0));
            r.setEndTime(LocalTime.of(16, 0));
        });
        register(cancelled, asAisyah);
        sessions.cancel(cancelled, "The trainer is unwell.", asWeiMing);
        at(today.plusDays(8), 15, 0);

        refused("This session was cancelled, so it has no attendance.",
                () -> record(cancelled, asWeiMing, entry(aisyah, ATTENDED, null)));
        refused("Nobody is on this session, so there is no attendance to submit.", () -> attendance.submit(empty, null, asWeiMing));
        at(today.plusDays(8), 16, 30);
        sessions.archive(empty, asWeiMing); // nobody registered, so nothing to submit (decision D)
        refused("This session is archived. Restore it to change its attendance.",
                () -> attendance.addWalkIn(empty, farid.getId(), null, asWeiMing));
        assertFalse(sessions.get(empty, asWeiMing).isCanRecordAttendance());
    }

    @Test
    @DisplayName("An ended session with people on it is archived only once its attendance is submitted (decision D)")
    void archiveAfterSubmitting() {
        Long id = session(r -> { });
        register(id, asAisyah);
        at(today.plusDays(8), 13, 0);

        refused("Submit the attendance first, then archive the session.", () -> sessions.archive(id, asWeiMing));
        record(id, asWeiMing, entry(aisyah, ATTENDED, null));
        attendance.submit(id, null, asWeiMing);

        assertEquals(TrainingSessionStatus.ARCHIVED, sessions.archive(id, asWeiMing).getStatus());
    }

    // --- evidence -------------------------------------------------------------------------------------

    @Test
    @DisplayName("Up to 5 photos or PDFs of up to 10 MB are attached, checked by what they contain (FR-14-08, decision B)")
    void evidence() throws IOException {
        Long id = session(r -> { });
        register(id, asAisyah);
        at(today.plusDays(8), 10, 0);

        TrainingAttendanceSheetDto sheet = attendance.addEvidence(id, file("sign-in sheet.png", PNG), asWeiMing);

        TrainingAttendanceSheetDto.Evidence first = sheet.getEvidence().get(0);
        assertEquals("sign-in sheet.png", first.getFileName());
        assertEquals("image/png", first.getContentType());
        assertEquals(PNG.length, first.getSizeBytes());
        assertEquals("Tan Wei Ming", first.getUploadedByName());
        TrainingAttendanceService.EvidenceFile stored = attendance.evidenceFile(id, first.getId(), asHr);
        assertArrayEquals(PNG, Files.readAllBytes(stored.path()));
        assertTrue(stored.path().startsWith(store.files().root.resolve("training-evidence").resolve(String.valueOf(id))));
        assertFalse(stored.path().getFileName().toString().contains("sign-in"), "stored under a generated name");
        assertThrows(ForbiddenRequestException.class, () -> attendance.evidenceFile(id, first.getId(), asAisyah));

        refused("Attach a photo (JPG or PNG) or a PDF.",
                () -> attendance.addEvidence(id, file("photo.png", "not a picture".getBytes()), asWeiMing));
        refused("Evidence files can be up to 10 MB.",
                () -> attendance.addEvidence(id, file("scan.pdf", new byte[10 * 1024 * 1024 + 1]), asWeiMing));
        attendance.addEvidence(id, file("C:\\Users\\trainer\\Desktop\\group photo.jpg", JPEG), asWeiMing);
        for (int i = 3; i <= 5; i++) {
            attendance.addEvidence(id, file("page " + i + ".pdf", PDF), asWeiMing);
        }
        refused("A session can have up to 5 evidence files. Remove one first.",
                () -> attendance.addEvidence(id, file("page 6.pdf", PDF), asWeiMing));
        sheet = attendance.sheet(id, asWeiMing);
        assertEquals("group photo.jpg", sheet.getEvidence().get(1).getFileName());
        assertEquals("image/jpeg", sheet.getEvidence().get(1).getContentType());
        assertEquals("application/pdf", sheet.getEvidence().get(2).getContentType());

        sheet = attendance.removeEvidence(id, first.getId(), asWeiMing);

        assertEquals(4, sheet.getEvidence().size());
        assertFalse(Files.exists(stored.path()));
        assertEquals("EVIDENCE_REMOVED", lastAudit().getAction());
        assertEquals("sign-in sheet.png", lastAudit().getDetail());
        refused("That file isn't attached to this session.", () -> attendance.evidenceFile(id, first.getId(), asHr));

        record(id, asWeiMing, entry(aisyah, ATTENDED, null));
        attendance.submit(id, null, asWeiMing);
        refused("Attendance is closed for the trainer. Only HR can change its evidence now.",
                () -> attendance.addEvidence(id, file("late.pdf", PDF), asWeiMing));
        assertEquals(5, attendance.addEvidence(id, file("late.pdf", PDF), asHr).getEvidence().size());
    }

    // --- awaiting attendance --------------------------------------------------------------------------

    @Test
    @DisplayName("Started sessions with people on them await attendance for their trainer and HR, oldest first (FR-14-10)")
    void awaitingAttendance() {
        Long later = session(r -> r.setDate(today.plusDays(9)));
        Long earlier = session(r -> { });
        Long nobody = session(r -> {
            r.setStartTime(LocalTime.of(14, 0));
            r.setEndTime(LocalTime.of(16, 0));
        });
        Long karims = session(asKarim, r -> {
            r.setTrainerId(karim.getId());
            r.setStartTime(LocalTime.of(14, 0));
            r.setEndTime(LocalTime.of(16, 0));
        });
        Long upcoming = session(r -> r.setDate(today.plusDays(10)));
        register(later, asAisyah);
        register(earlier, asAisyah);
        register(karims, asAisyah);
        register(upcoming, asRavi);
        at(today.plusDays(9), 9, 30);

        assertEquals(List.of(earlier, later), ids(sessions.awaitingAttendance(asWeiMing)));
        assertEquals(List.of(earlier, karims, later), ids(sessions.awaitingAttendance(asHr)));
        assertEquals(List.of(karims), ids(sessions.awaitingAttendance(asKarim)));
        assertTrue(sessions.awaitingAttendance(asAisyah).isEmpty());
        assertFalse(ids(sessions.awaitingAttendance(asHr)).contains(nobody));
        assertFalse(ids(sessions.awaitingAttendance(asHr)).contains(upcoming));

        record(earlier, asWeiMing, entry(aisyah, ATTENDED, null));
        attendance.submit(earlier, null, asWeiMing);

        assertEquals(List.of(later), ids(sessions.awaitingAttendance(asWeiMing)));
    }
}
