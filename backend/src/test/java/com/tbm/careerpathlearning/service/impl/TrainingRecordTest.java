package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrainingRecordDto;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.enums.TrainingAttendanceMethod;
import com.tbm.careerpathlearning.enums.TrainingAttendanceStatus;
import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.enums.TrainingType;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAttendance;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A staff member's training record, and who may see it (FR-18-01 to FR-18-05, decisions A and B of 29 Oct 2026). */
class TrainingRecordTest {

    private SopTestStore store;
    private TrainingRecordServiceImpl records;
    private Staff hr, weiMing, jason, aisyah, ravi;
    private TrainingViewer asHr, asWeiMing, asJason, asAisyah, asRavi, asSuperAdmin;
    private LocalDate today;
    private long nextSession = 900;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        records = store.wire(new TrainingRecordServiceImpl());
        OrgChart sales = store.department(10L, "Sales Department");
        OrgChart people = store.department(12L, "Human Resources");
        Role salesExecutive = store.role(20L, "Sales Executive", sales);
        Role supervisor = store.role(21L, "Outlet Supervisor", sales);
        Role trainer = store.role(22L, "Trainer", people);
        Role hrExecutive = store.role(23L, "HR Executive", people);
        store.permit(trainer, AuthorityName.CAN_MANAGE_TRAINING);
        store.permit(hrExecutive, AuthorityName.CAN_ASSIGN_TRAINING);
        hr = store.person("Hana Yusof", hrExecutive);
        weiMing = store.person("Tan Wei Ming", trainer);
        jason = store.person("Jason Lim", supervisor);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        ravi = store.person("Ravi Kumar", salesExecutive);
        aisyah.setManager(jason);
        asHr = new TrainingViewer(hr.getId(), false, true, false);
        asWeiMing = new TrainingViewer(weiMing.getId(), true, false, false);
        asJason = new TrainingViewer(jason.getId(), false, false, false);
        asAisyah = new TrainingViewer(aisyah.getId(), false, false, false);
        asRavi = new TrainingViewer(ravi.getId(), false, false, false);
        asSuperAdmin = new TrainingViewer(UUID.randomUUID(), false, false, true);
        today = store.today(); // Monday 12 Oct 2026
    }

    /** A course of two modules of 5 questions each. */
    private SopDocument course(long id, String title) {
        SopDocument d = store.doc(id, title, weiMing.getId());
        d.setVersionNo(1);
        store.module(id, 5);
        store.module(id, 5);
        return d;
    }

    private List<SopModule> modulesOf(long docId) {
        return store.modules.values().stream().filter(m -> m.getSopDocumentId().equals(docId)).toList();
    }

    private SopEnrollment enrol(long docId, Staff who, LocalDate effective, int days, boolean mandatory) {
        SopEnrollment e = store.enrollment(docId, who.getId(), SopAssignmentMode.IMMEDIATE, effective, null, days, 70);
        e.setMandatory(mandatory);
        return e;
    }

    private TrainingProgram session(String title, LocalDate day, TrainingType type, boolean submitted) {
        TrainingProgram s = new TrainingProgram();
        s.setTrainingId(nextSession++);
        s.setTitle(title);
        s.setKind(TrainingSessionKind.TRAINING);
        s.setDelivery(TrainingDelivery.PHYSICAL);
        s.setTrainingType(type);
        s.setSessionDate(day);
        s.setStartTime(LocalTime.of(9, 0));
        s.setEndTime(LocalTime.of(12, 0));
        s.setCapacity(20);
        s.setStatus(TrainingSessionStatus.SCHEDULED);
        s.setAttendanceSubmittedAt(submitted ? LocalDateTime.of(day, LocalTime.of(13, 0)) : null);
        store.sessions.put(s.getTrainingId(), s);
        return s;
    }

    private void mark(TrainingProgram s, Staff who, TrainingAttendanceStatus status, Integer score) {
        TrainingAttendance a = new TrainingAttendance();
        a.setAttendanceId(nextSession++);
        a.setTrainingId(s.getTrainingId());
        a.setStaffId(who.getId());
        a.setStatus(status);
        a.setQuizScore(score);
        a.setMethod(TrainingAttendanceMethod.MANUAL);
        a.setRecordedBy(weiMing.getId());
        a.setRecordedAt(LocalDateTime.of(s.getSessionDate(), LocalTime.NOON));
        store.attendance.put(a.getAttendanceId(), a);
    }

    private static TrainingRecordDto.Item item(TrainingRecordDto r, String title) {
        return r.getItems().stream().filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();
    }

    /**
     * Nur Aisyah: E-Hailing completed two days ago in 90 minutes; Cash Handling, mandatory, overdue; Old Pricing,
     * withdrawn; Store Opening, optional and past its date; a Product Training attended yesterday with a quiz of 85%; a
     * session missed; and a session whose attendance isn't submitted yet.
     */
    private void history() {
        course(40, "E-Hailing");
        SopEnrollment done = enrol(40, aisyah, today.minusDays(9), 7, false);
        done.setStatus(SopEnrollmentStatus.COMPLETED);
        done.setCompletedAt(store.now().minusDays(2));
        done.setFinalScore(85);
        done.setDurationMinutes(90);
        List<SopModule> m = modulesOf(40);
        store.attempt(done, m.get(0).getId(), 60, false, store.now().minusDays(3));
        store.attempt(done, m.get(0).getId(), 80, true, store.now().minusDays(3).plusHours(1));
        store.attempt(done, m.get(1).getId(), 90, true, store.now().minusDays(2));

        course(41, "Cash Handling");
        SopEnrollment late = enrol(41, aisyah, today.minusDays(10), 7, true);
        late.setStatus(SopEnrollmentStatus.IN_PROGRESS);
        store.attempt(late, modulesOf(41).get(0).getId(), 40, false, store.now().minusDays(4));

        course(42, "Old Pricing");
        enrol(42, aisyah, today.minusDays(30), 7, false).setStatus(SopEnrollmentStatus.WITHDRAWN);
        course(43, "Store Opening");
        enrol(43, aisyah, today.minusDays(20), 7, false);

        mark(session("Inverter Launch", today.minusDays(1), TrainingType.PRODUCT, true), aisyah, TrainingAttendanceStatus.ATTENDED, 85);
        mark(session("Safety Briefing", today.minusDays(5), TrainingType.OTHER, true), aisyah, TrainingAttendanceStatus.ABSENT, null);
        mark(session("Not Yet Submitted", today.minusDays(1), TrainingType.OTHER, false), aisyah, TrainingAttendanceStatus.ATTENDED, null);
    }

    @Test
    @DisplayName("The record lists every course and submitted session with its status, dates, scores and hours (FR-18-01, FR-18-02)")
    void items() {
        history();

        TrainingRecordDto r = records.record(aisyah.getId(), asAisyah);

        assertEquals("Nur Aisyah", r.getName());
        assertEquals("Sales Department", r.getDepartmentName());
        assertEquals(List.of("Inverter Launch", "E-Hailing", "Cash Handling", "Safety Briefing", "Store Opening", "Old Pricing"),
                r.getItems().stream().map(TrainingRecordDto.Item::getTitle).toList());

        TrainingRecordDto.Item done = item(r, "E-Hailing");
        assertEquals("COURSE", done.getType());
        assertEquals("COMPLETED", done.getStatus());
        assertEquals("E-learning", done.getDelivery());
        assertEquals(today.minusDays(2), done.getCompletedOn());
        assertEquals(today.minusDays(2), done.getDueDate()); // due and completed are shown apart (TF-17)
        assertEquals(85, done.getScore());
        assertEquals(1.5, done.getHours());
        assertEquals(80, done.getModules().get(0).getScore());
        assertTrue(done.getModules().get(0).isPassed());
        assertEquals(2, done.getModules().get(0).getAttempts());
        assertEquals(90, done.getModules().get(1).getScore());

        TrainingRecordDto.Item late = item(r, "Cash Handling");
        assertEquals("OVERDUE", late.getStatus());
        assertTrue(late.isMandatory());
        assertEquals(today.minusDays(3), late.getDueDate());
        assertNull(late.getCompletedOn());
        assertNull(late.getHours());
        assertEquals(40, late.getModules().get(0).getScore());
        assertFalse(late.getModules().get(0).isPassed());
        assertNull(late.getModules().get(1).getScore());
        assertEquals("WITHDRAWN", item(r, "Old Pricing").getStatus());
        assertEquals("NOT_STARTED", item(r, "Store Opening").getStatus()); // optional: never Overdue (FR-11-17)

        TrainingRecordDto.Item attended = item(r, "Inverter Launch");
        assertEquals("SESSION", attended.getType());
        assertEquals("ATTENDED", attended.getStatus());
        assertEquals("Physical", attended.getDelivery());
        assertEquals("Training", attended.getKind());
        assertEquals("Product Training", attended.getTrainingType());
        assertEquals(today.minusDays(1), attended.getHeldOn());
        assertEquals(today.minusDays(1), attended.getCompletedOn());
        assertEquals(85, attended.getScore());
        assertEquals(3.0, attended.getHours());
        TrainingRecordDto.Item missed = item(r, "Safety Briefing");
        assertEquals("ABSENT", missed.getStatus());
        assertNull(missed.getCompletedOn());
        assertNull(missed.getHours());
    }

    @Test
    @DisplayName("The totals count completed courses and attended sessions, with their hours; certificates are listed (FR-18-03, FR-18-05)")
    void totalsAndCertificates() {
        history();

        TrainingRecordDto r = records.record(aisyah.getId(), asAisyah);

        assertEquals(2, r.getCompletedItems());
        assertEquals(1.5, r.getCourseHours());
        assertEquals(3.0, r.getSessionHours());
        assertEquals(4.5, r.getTotalHours());
        assertEquals(today.minusDays(1), r.getLastCompleted());
        assertEquals(1, r.getMandatoryOutstanding());
        assertEquals(1, r.getCertificates().size());
        TrainingRecordDto.Certificate c = r.getCertificates().get(0);
        assertEquals(store.enrollmentOf(40, aisyah.getId()).certificateNo(), c.getCertificateNo());
        assertTrue(c.getCertificateNo().startsWith("APEX-SOP-40-"));
        assertEquals("E-Hailing", c.getCourse());
        assertEquals(1, c.getVersionNo());
        assertEquals(today.minusDays(2), c.getIssuedOn());
        assertEquals(85, c.getFinalScore());

        TrainingRecordDto empty = records.record(ravi.getId(), asRavi);
        assertTrue(empty.getItems().isEmpty());
        assertEquals(0.0, empty.getTotalHours());
        assertNull(empty.getLastCompleted());
    }

    @Test
    @DisplayName("Staff see their own record, managers their direct reports', HR and the Super Admin anyone's (decision A)")
    void whoSeesIt() {
        history();

        assertEquals(6, records.record(aisyah.getId(), asJason).getItems().size());
        assertEquals(6, records.record(aisyah.getId(), asHr).getItems().size());
        assertEquals(6, records.record(aisyah.getId(), asSuperAdmin).getItems().size());
        assertThrows(ForbiddenRequestException.class, () -> records.record(aisyah.getId(), asRavi));
        assertThrows(ForbiddenRequestException.class, () -> records.record(aisyah.getId(), asWeiMing));
        assertThrows(ForbiddenRequestException.class, () -> records.record(ravi.getId(), asJason)); // not Jason's report

        ravi.setAccountStatus(StaffAccountStatus.INACTIVE);
        assertTrue(records.record(ravi.getId(), asHr).isLeftCompany());
    }
}
