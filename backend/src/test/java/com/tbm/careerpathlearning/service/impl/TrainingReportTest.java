package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.TrainingAttendanceRequest;
import com.tbm.careerpathlearning.dto.TrainingRegistrationRequest;
import com.tbm.careerpathlearning.dto.TrainingSessionReportDto;
import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.TrainingAttendanceStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingType;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAuditLog;
import com.tbm.careerpathlearning.service.TrainingDownload;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A session's report on screen and as an Excel workbook (FR-12-07, decision D of 28 Oct 2026). */
class TrainingReportTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    private SopTestStore store;
    private TrainingServiceImpl sessions;
    private TrainingRegistrationServiceImpl registrations;
    private TrainingAttendanceServiceImpl attendance;
    private TrainingReportServiceImpl reports;
    private Staff aisyah, ravi, siti, farid, karim;
    private TrainingViewer asHr, asWeiMing, asKarim, asAisyah, asRavi, asSiti, asFarid, asSuperAdmin;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        sessions = store.wire(new TrainingServiceImpl());
        registrations = (TrainingRegistrationServiceImpl) store.sessionRegistrations();
        attendance = store.wire(new TrainingAttendanceServiceImpl());
        reports = store.wire(new TrainingReportServiceImpl());
        OrgChart sales = store.department(10L, "Sales Department");
        OrgChart service = store.department(11L, "Service Department");
        OrgChart people = store.department(12L, "Human Resources");
        Role salesExecutive = store.role(20L, "Sales Executive", sales);
        Role technician = store.role(21L, "Technician", service);
        Role trainer = store.role(22L, "Trainer", people);
        Role hrExecutive = store.role(23L, "HR Executive", people);
        store.permit(trainer, AuthorityName.CAN_MANAGE_TRAINING);
        store.permit(hrExecutive, AuthorityName.CAN_ASSIGN_TRAINING);
        Staff hr = store.person("Hana Yusof", hrExecutive);
        Staff weiMing = store.person("Tan Wei Ming", trainer);
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
        asFarid = new TrainingViewer(farid.getId(), false, false, false);
        asSuperAdmin = new TrainingViewer(UUID.randomUUID(), false, false, true);
        today = store.today(); // Monday 12 Oct 2026

        TrainingSessionRequest r = new TrainingSessionRequest();
        r.setTitle("Inverter Series 2026 Launch");
        r.setKind(TrainingSessionKind.TRAINING);
        r.setDelivery(TrainingDelivery.PHYSICAL);
        r.setTrainingType(TrainingType.PRODUCT);
        r.setDate(today.plusDays(8));
        r.setStartTime(LocalTime.of(9, 0));
        r.setEndTime(LocalTime.of(12, 0));
        r.setVenue("Conference Hall B, HQ");
        r.setCapacity(3);
        r.setMandatory(true);
        r.setTrainerId(weiMing.getId());
        r.setRegistrationForm(true);
        sessions.schedule(r, asWeiMing);
    }

    private Long id() {
        return store.sessions.keySet().iterator().next();
    }

    private void register(TrainingViewer viewer, String idNumber) {
        TrainingRegistrationRequest form = new TrainingRegistrationRequest();
        form.setIdNumber(idNumber);
        form.setPhone("012-345 6789");
        form.setBranch("IOI Mall Damansara");
        form.setPdpaConsent(true);
        registrations.register(id(), form, viewer);
    }

    private TrainingAttendanceRequest.Entry entry(Staff s, TrainingAttendanceStatus status, Integer score) {
        TrainingAttendanceRequest.Entry e = new TrainingAttendanceRequest.Entry();
        e.setStaffId(s.getId());
        e.setStatus(status);
        e.setQuizScore(score);
        return e;
    }

    /** Three registered, Farid waitlisted, Karim a walk-in; Siti absent; a photo attached. */
    private void heldAndMarked() {
        register(asAisyah, "010203-10-1234");
        register(asRavi, "900101-14-5678");
        register(asSiti, "A1234567");
        register(asFarid, "880808-08-8888"); // waitlisted: 3 seats
        store.clock.set(LocalDateTime.of(today.plusDays(8), LocalTime.of(12, 30)).atZone(ClockConfig.ZONE).toInstant());
        TrainingAttendanceRequest marks = new TrainingAttendanceRequest();
        marks.setEntries(List.of(entry(aisyah, TrainingAttendanceStatus.ATTENDED, 80),
                entry(ravi, TrainingAttendanceStatus.ATTENDED, 90), entry(siti, TrainingAttendanceStatus.ABSENT, null)));
        attendance.record(id(), marks, asWeiMing);
        attendance.addWalkIn(id(), karim.getId(), null, asWeiMing);
        attendance.addEvidence(id(), new MockMultipartFile("file", "group photo.png", "image/png", PNG), asWeiMing);
    }

    private static XSSFWorkbook open(TrainingDownload file) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(file.content()));
    }

    /** Every row of the sheet, each as its cells' text. */
    private static List<List<String>> rows(Sheet sheet) {
        DataFormatter f = new DataFormatter();
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            List<String> cells = new ArrayList<>();
            if (row != null) row.forEach(c -> cells.add(f.formatCellValue(c)));
            rows.add(cells);
        }
        return rows;
    }

    /** The value beside a label in the report's top part. */
    private static String value(List<List<String>> rows, String label) {
        return rows.stream().filter(r -> r.size() > 1 && r.get(0).equals(label)).map(r -> r.get(1)).findFirst().orElseThrow();
    }

    private static List<String> person(List<List<String>> rows, String name) {
        return rows.stream().filter(r -> !r.isEmpty() && r.get(0).equals(name) && r.size() > 2).findFirst().orElseThrow();
    }

    private List<TrainingAuditLog> exports() {
        return store.auditLog.values().stream()
                .filter(a -> a.getAction().equals(TrainingAuditAction.IC_NUMBERS_EXPORTED.name())).toList();
    }

    @Test
    @DisplayName("The report counts the session's people and its attendance, with IC numbers in full for its trainer and HR")
    void numbers() {
        heldAndMarked();

        TrainingSessionReportDto r = reports.report(id(), asWeiMing);

        assertEquals("Inverter Series 2026 Launch", r.getTitle());
        assertEquals("Tan Wei Ming", r.getTrainerName());
        assertEquals(180, r.getDurationMinutes());
        assertEquals(3, r.getCapacity());
        assertEquals(3, r.getRegistered());
        assertEquals(1, r.getWaitlisted());
        assertEquals(1, r.getWalkIns());
        assertEquals(3, r.getAttended());
        assertEquals(1, r.getAbsent());
        assertEquals(0, r.getNotRecorded());
        assertEquals(75, r.getAttendanceRate());
        assertEquals(9.0, r.getTrainingHours());
        assertEquals(85, r.getAverageQuizScore());
        assertFalse(r.isSubmitted());
        assertEquals(List.of("Karim Abdullah", "Nur Aisyah", "Ravi Kumar", "Siti Aminah"),
                r.getPeople().stream().map(TrainingSessionReportDto.Person::getName).toList());
        TrainingSessionReportDto.Person a = r.getPeople().get(1);
        assertEquals("nur.aisyah@tbm.net", a.getEmail());
        assertEquals("010203101234", a.getIdNumber());
        assertEquals("IOI Mall Damansara", a.getBranch());
        assertEquals("Sales Executive", a.getRoleName());
        assertEquals("Sales Department", a.getDepartmentName());
        assertEquals("ATTENDED", a.getStatus());
        assertEquals(80, a.getQuizScore());
        assertEquals("MANUAL", a.getMethod());
        assertTrue(r.getPeople().get(0).isWalkIn());
        assertNull(r.getPeople().get(0).getIdNumber()); // a walk-in filled in no form
        assertEquals(List.of("group photo.png"), r.getEvidence().stream().map(e -> e.getFileName()).toList());
        assertEquals("Tan Wei Ming", r.getGeneratedByName());
        // The Super Admin, without HR's permission, sees the report and attendance without the HRDC details.
        assertNull(reports.report(id(), asSuperAdmin).getPeople().get(1).getIdNumber());
        assertNull(attendance.sheet(id(), asSuperAdmin).getRows().stream()
                .filter(x -> x.getName().equals("Nur Aisyah")).findFirst().orElseThrow().getIdNumber());

        attendance.submit(id(), null, asWeiMing);
        TrainingSessionReportDto submitted = reports.report(id(), asHr);
        assertTrue(submitted.isSubmitted());
        assertEquals("Tan Wei Ming", submitted.getSubmittedByName());
        assertEquals("010203101234", submitted.getPeople().get(1).getIdNumber());
    }

    @Test
    @DisplayName("The session's trainer, HR and the Super Admin see the report; nobody else does (FR-12-07)")
    void whoSeesIt() {
        assertNotNull(reports.report(id(), asWeiMing));
        assertNotNull(reports.report(id(), asHr));
        assertNotNull(reports.report(id(), asSuperAdmin));
        assertNull(reports.report(id(), asWeiMing).getAttendanceRate()); // nobody on it yet
        assertThrows(ForbiddenRequestException.class, () -> reports.report(id(), asKarim));
        assertThrows(ForbiddenRequestException.class, () -> reports.report(id(), asAisyah));
        assertThrows(ForbiddenRequestException.class, () -> reports.reportFile(id(), asAisyah));
    }

    @Test
    @DisplayName("The Excel report keeps IC numbers in full, as text; each download with them is logged (decision D)")
    void excel() throws IOException {
        heldAndMarked();

        TrainingDownload trainers = reports.reportFile(id(), asWeiMing);
        assertEquals("Session report - Inverter Series 2026 Launch - 2026-10-20.xlsx", trainers.fileName());
        try (XSSFWorkbook workbook = open(trainers)) {
            List<List<String>> rows = rows(workbook.getSheet("Report"));
            assertEquals("Session report", rows.get(0).get(0));
            assertEquals("Tue 20 Oct 2026, 09:00 to 12:00 (3 h)", value(rows, "Date and time"));
            assertEquals("Product Training", value(rows, "Training type"));
            assertEquals("Ended", value(rows, "Status"));
            assertEquals("Not submitted yet", value(rows, "Attendance"));
            assertEquals("75%", value(rows, "Attendance rate"));
            assertEquals("9", value(rows, "Training hours (attended x length)"));
            assertEquals(List.of("Nur Aisyah", "nur.aisyah@tbm.net", "010203101234", "Sales Executive", "Sales Department",
                    "IOI Mall Damansara", "No", "Attended", "80", "By hand", "Tan Wei Ming", "20 Oct 2026, 12:30"),
                    person(rows, "Nur Aisyah"));
            assertEquals("Yes", person(rows, "Karim Abdullah").get(6));
            assertTrue(rows.stream().anyMatch(r -> !r.isEmpty() && r.get(0).equals("group photo.png")));
            assertEquals("Made by Tan Wei Ming on 20 Oct 2026, 12:30, Malaysia time.", rows.get(rows.size() - 1).get(0));
        }
        assertEquals(1, exports().size());

        try (XSSFWorkbook workbook = open(reports.reportFile(id(), asHr))) {
            Sheet sheet = workbook.getSheet("Report");
            Cell ic = null;
            for (Row row : sheet) {
                if (row.getCell(0) != null && "Nur Aisyah".equals(row.getCell(0).getStringCellValue()) && row.getCell(2) != null) {
                    ic = row.getCell(2);
                }
            }
            assertNotNull(ic);
            assertEquals(CellType.STRING, ic.getCellType());
            assertEquals("010203101234", ic.getStringCellValue());
        }
        assertEquals(2, exports().size());
        assertEquals("Session report with IC numbers", exports().get(1).getDetail());
    }

    @Test
    @DisplayName("A file name keeps the title but drops characters Windows refuses")
    void fileName() {
        store.sessions.get(id()).setTitle("Q4/Q1: Review?");
        assertEquals("Session report - Q4-Q1- Review- - 2026-10-20.xlsx",
                TrainingSpreadsheets.fileName("Session report", store.sessions.get(id())));
    }
}
