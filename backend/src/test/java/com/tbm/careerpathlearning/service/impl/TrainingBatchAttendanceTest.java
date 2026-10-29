package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.TrainingAttendanceRequest;
import com.tbm.careerpathlearning.dto.TrainingAttendanceSheetDto;
import com.tbm.careerpathlearning.dto.TrainingBatchResultDto;
import com.tbm.careerpathlearning.dto.TrainingRegistrationRequest;
import com.tbm.careerpathlearning.dto.TrainingSessionRequest;
import com.tbm.careerpathlearning.enums.AuthorityName;
import com.tbm.careerpathlearning.enums.TrainingAttendanceStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingDelivery;
import com.tbm.careerpathlearning.enums.TrainingSessionKind;
import com.tbm.careerpathlearning.enums.TrainingType;
import com.tbm.careerpathlearning.exception.BadRequestException;
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
import org.junit.jupiter.api.function.Executable;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The HRDC attendance file: downloading it pre-filled, and uploading it filled in to record everyone at once
 * (FR-14-05 to FR-14-07, decisions B1, B2, C and D of 28 Oct 2026).
 */
class TrainingBatchAttendanceTest {

    private SopTestStore store;
    private TrainingServiceImpl sessions;
    private TrainingRegistrationServiceImpl registrations;
    private TrainingAttendanceServiceImpl attendance;
    private Staff weiMing, aisyah, ravi, farid;
    private TrainingViewer asHr, asWeiMing, asKarim, asAisyah, asRavi;
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
        Staff hr = store.person("Hana Yusof", hrExecutive);
        weiMing = store.person("Tan Wei Ming", trainer);
        Staff karim = store.person("Karim Abdullah", trainer);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        ravi = store.person("Ravi Kumar", salesExecutive);
        farid = store.person("Ahmad Farid", technician);
        asHr = new TrainingViewer(hr.getId(), false, true, false);
        asWeiMing = new TrainingViewer(weiMing.getId(), true, false, false);
        asKarim = new TrainingViewer(karim.getId(), true, false, false);
        asAisyah = new TrainingViewer(aisyah.getId(), false, false, false);
        asRavi = new TrainingViewer(ravi.getId(), false, false, false);
        today = store.today(); // Monday 12 Oct 2026
    }

    /** A Physical product training with the HRDC form, Tuesday 20 Oct 2026, 09:00 to 12:00, run by Tan Wei Ming. */
    private Long session(Consumer<TrainingSessionRequest> change) {
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
        r.setRegistrationForm(true);
        change.accept(r);
        return sessions.schedule(r, asWeiMing).getId();
    }

    private void register(Long id, TrainingViewer viewer, String idNumber) {
        TrainingRegistrationRequest form = new TrainingRegistrationRequest();
        form.setIdNumber(idNumber);
        form.setPhone("012-345 6789");
        form.setBranch("Bangsar Village");
        form.setPdpaConsent(true);
        registrations.register(id, form, viewer);
    }

    /** Aisyah and Ravi registered; the session has started. */
    private Long started() {
        Long id = session(r -> { });
        register(id, asAisyah, "010203-10-1234");
        register(id, asRavi, "A1234567");
        at(today.plusDays(8), 12, 30);
        return id;
    }

    private void at(LocalDate date, int hour, int minute) {
        store.clock.set(LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(ClockConfig.ZONE).toInstant());
    }

    private void mark(Long id, Staff s, TrainingAttendanceStatus status) {
        TrainingAttendanceRequest.Entry e = new TrainingAttendanceRequest.Entry();
        e.setStaffId(s.getId());
        e.setStatus(status);
        TrainingAttendanceRequest r = new TrainingAttendanceRequest();
        r.setEntries(List.of(e));
        attendance.record(id, r, asWeiMing);
    }

    private static XSSFWorkbook open(TrainingDownload file) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(file.content()));
    }

    private static String text(Sheet sheet, int row, int column) {
        Row r = sheet.getRow(row);
        return r == null ? "" : new DataFormatter().formatCellValue(r.getCell(column));
    }

    /**
     * The downloaded template with Attended and the quiz score filled in for the people named, and any extra rows
     * typed in under the list.
     */
    private static MockMultipartFile filled(TrainingDownload template, Map<String, String[]> marks, String[]... extra)
            throws IOException {
        try (XSSFWorkbook workbook = open(template)) {
            Sheet sheet = workbook.getSheet("Attendance");
            int last = sheet.getLastRowNum();
            for (int i = 6; i <= last; i++) {
                String[] m = marks.get(text(sheet, i, 1));
                if (m == null) continue;
                sheet.getRow(i).getCell(6).setCellValue(m[0]);
                if (m.length > 1) sheet.getRow(i).getCell(7).setCellValue(m[1]);
            }
            for (String[] row : extra) {
                Row r = sheet.createRow(++last);
                for (int c = 0; c < row.length; c++) r.createCell(c).setCellValue(row[c]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return file("attendance.xlsx", out.toByteArray());
        }
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private static MockMultipartFile csv(String text) {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        return file("attendance.csv", all);
    }

    private static TrainingAttendanceSheetDto.Row row(TrainingAttendanceSheetDto sheet, Staff s) {
        return sheet.getRows().stream().filter(r -> r.getStaffId().equals(s.getId())).findFirst().orElseThrow();
    }

    private static void refused(String message, Executable action) {
        assertEquals(message, assertThrows(BadRequestException.class, action).getMessage());
    }

    private List<TrainingAuditLog> audits(TrainingAuditAction action) {
        return store.auditLog.values().stream().filter(a -> a.getAction().equals(action.name())).toList();
    }

    @Test
    @DisplayName("The template lists everyone on the session with marks so far; only HR's copy has full IC numbers (FR-14-05)")
    void template() throws IOException {
        Long id = started();
        mark(id, ravi, TrainingAttendanceStatus.ABSENT);
        attendance.addWalkIn(id, farid.getId(), null, asWeiMing);

        TrainingDownload trainers = attendance.hrdcTemplate(id, asWeiMing);

        assertEquals("HRDC attendance - Inverter Series 2026 Launch - 2026-10-20.xlsx", trainers.fileName());
        assertEquals(TrainingSpreadsheets.XLSX, trainers.contentType());
        try (XSSFWorkbook workbook = open(trainers)) {
            Sheet sheet = workbook.getSheet("Attendance");
            assertEquals("HRDC attendance", text(sheet, 0, 0));
            assertEquals("Inverter Series 2026 Launch", text(sheet, 1, 0));
            assertEquals("Tue 20 Oct 2026, 09:00 to 12:00 · Conference Hall B, HQ · Trainer: Tan Wei Ming", text(sheet, 2, 0));
            List<String> header = new ArrayList<>();
            sheet.getRow(5).forEach(c -> header.add(c.getStringCellValue()));
            assertEquals(List.of(TrainingAttendanceServiceImpl.HRDC_COLUMNS), header);
            assertEquals("Ahmad Farid", text(sheet, 6, 1)); // a walk-in: no HRDC form
            assertEquals("", text(sheet, 6, 2));
            assertEquals("Y", text(sheet, 6, 6));
            assertEquals("nur.aisyah@tbm.net", text(sheet, 7, 0));
            assertEquals("********1234", text(sheet, 7, 2));
            assertEquals("Sales Executive", text(sheet, 7, 3));
            assertEquals("Sales Department", text(sheet, 7, 4));
            assertEquals("Bangsar Village", text(sheet, 7, 5));
            assertEquals("", text(sheet, 7, 6));
            assertEquals("****4567", text(sheet, 8, 2));
            assertEquals("N", text(sheet, 8, 6));
            Sheet about = workbook.getSheet("About");
            assertTrue(workbook.isSheetHidden(workbook.getSheetIndex(about)));
            assertEquals(String.valueOf(id), text(about, 0, 1));
            assertEquals(2, sheet.getDataValidations().size());
        }
        assertTrue(audits(TrainingAuditAction.IC_NUMBERS_EXPORTED).isEmpty());

        try (XSSFWorkbook workbook = open(attendance.hrdcTemplate(id, asHr))) {
            Cell ic = workbook.getSheet("Attendance").getRow(7).getCell(2);
            assertEquals(CellType.STRING, ic.getCellType());
            assertEquals("010203101234", ic.getStringCellValue()); // text, so Excel keeps the leading zero
        }
        TrainingAuditLog logged = audits(TrainingAuditAction.IC_NUMBERS_EXPORTED).get(0);
        assertEquals("HRDC attendance template with IC numbers", logged.getDetail());
        assertEquals(String.valueOf(id), logged.getTargetId());

        assertThrows(ForbiddenRequestException.class, () -> attendance.hrdcTemplate(id, asKarim));
        assertThrows(ForbiddenRequestException.class, () -> attendance.hrdcTemplate(id, asAisyah));
    }

    @Test
    @DisplayName("An uploaded template marks everyone in one go, recorded as Batch, and says what it did (FR-14-06, FR-14-07)")
    void upload() throws IOException {
        Long id = started();
        mark(id, ravi, TrainingAttendanceStatus.ABSENT);
        TrainingDownload template = attendance.hrdcTemplate(id, asWeiMing);

        TrainingBatchResultDto result = attendance.uploadBatch(id, filled(template, Map.of(
                "Nur Aisyah", new String[]{"y", "85"},
                "Ravi Kumar", new String[]{"N"})), null, asWeiMing);

        assertEquals(2, result.getRows());
        assertEquals(2, result.getMatched());
        assertEquals(1, result.getAttended());
        assertEquals(1, result.getAbsent());
        assertEquals(1, result.getChanged()); // Ravi was already Absent
        assertTrue(result.getUnmatched().isEmpty());
        TrainingAttendanceSheetDto.Row a = row(result.getSheet(), aisyah);
        assertEquals("ATTENDED", a.getStatus());
        assertEquals(85, a.getQuizScore());
        assertEquals("BATCH", a.getMethod());
        assertEquals("Tan Wei Ming", a.getRecordedByName());
        assertEquals("MANUAL", row(result.getSheet(), ravi).getMethod());
        assertTrue(result.getSheet().isCanSubmit());
        assertTrue(audits(TrainingAuditAction.ATTENDANCE_CHANGED).isEmpty()); // the trainer, within the 7 days
    }

    @Test
    @DisplayName("A blank Attended cell changes nothing; people not on the session are listed, not added (decisions B1, B2)")
    void blankAndUnmatched() throws IOException {
        Long id = started();
        mark(id, aisyah, TrainingAttendanceStatus.ABSENT);
        TrainingDownload template = attendance.hrdcTemplate(id, asWeiMing);

        TrainingBatchResultDto result = attendance.uploadBatch(id, filled(template, Map.of(
                        "Nur Aisyah", new String[]{""},
                        "Ravi Kumar", new String[]{"Y"}),
                new String[]{"", "Ali Bakar", "", "", "", "", "Y", ""},
                new String[]{"nobody@tbm.net", "", "", "", "", "", "Y", ""},
                new String[]{"farid@example.com", "ahmad farid", "", "", "", "", "Y", ""}), null, asWeiMing);

        assertEquals(5, result.getRows());
        assertEquals(2, result.getMatched());
        assertEquals(1, result.getLeftBlank());
        assertEquals(1, result.getAttended());
        assertEquals(List.of("Row 9, Ali Bakar: not on this session", "Row 10, nobody@tbm.net: not on this session",
                "Row 11, ahmad farid: not on this session"), result.getUnmatched());
        assertEquals("ABSENT", row(result.getSheet(), aisyah).getStatus());
        assertEquals("MANUAL", row(result.getSheet(), aisyah).getMethod());
        assertEquals(2, result.getSheet().getRows().size()); // nobody was added as a walk-in
    }

    @Test
    @DisplayName("Any invalid or repeated row refuses the whole file, naming the rows; nothing is saved")
    void invalidRows() throws IOException {
        Long id = started();
        TrainingDownload template = attendance.hrdcTemplate(id, asWeiMing);

        refused("Nothing was saved. Fix the file, then upload it again: row 7: Attended must be Y or N, "
                        + "not \"maybe\"; row 8: the quiz score must be a whole number from 0 to 100, not \"150\".",
                () -> attendance.uploadBatch(id, filled(template, Map.of(
                        "Nur Aisyah", new String[]{"maybe"},
                        "Ravi Kumar", new String[]{"Y", "150"})), null, asWeiMing));
        refused("Nothing was saved. Fix the file, then upload it again: row 7: a quiz score goes with "
                        + "Attended (Y) only; row 8: fill in Attended (Y) to go with the quiz score.",
                () -> attendance.uploadBatch(id, filled(template, Map.of(
                        "Nur Aisyah", new String[]{"N", "40"},
                        "Ravi Kumar", new String[]{"", "60"})), null, asWeiMing));
        refused("Nothing was saved. Fix the file, then upload it again: rows 7 and 9 are both Nur Aisyah, "
                        + "so keep one.",
                () -> attendance.uploadBatch(id, filled(template, Map.of("Nur Aisyah", new String[]{"Y"}),
                        new String[]{"nur.aisyah@tbm.net", "Nur Aisyah", "", "", "", "", "N", ""}), null, asWeiMing));
        assertTrue(store.attendance.isEmpty());
    }

    @Test
    @DisplayName("A CSV works too: by name when there are no emails, with ; or , and Malay or English marks")
    void csvUpload() {
        Long id = started();

        TrainingBatchResultDto result = attendance.uploadBatch(id, csv("""
                Full name;Attended (Y/N);Quiz score
                  nur   AISYAH ;hadir;90%
                "Ravi Kumar";tidak;
                "Bakar; Ali";Y;
                """), null, asWeiMing);

        assertEquals(3, result.getRows());
        assertEquals("ATTENDED", row(result.getSheet(), aisyah).getStatus());
        assertEquals(90, row(result.getSheet(), aisyah).getQuizScore());
        assertEquals("ABSENT", row(result.getSheet(), ravi).getStatus());
        assertEquals(List.of("Row 4, Bakar; Ali: not on this session"), result.getUnmatched());

        result = attendance.uploadBatch(id, csv("Email,Attended (Y/N),Quiz score\r\nravi.kumar@tbm.net,Y,70\r\n"), null, asWeiMing);
        assertEquals("ATTENDED", row(result.getSheet(), ravi).getStatus());
        assertEquals(70, row(result.getSheet(), ravi).getQuizScore());
    }

    @Test
    @DisplayName("Another session's template, old .xls files, and files without the columns are refused")
    void wrongFiles() throws IOException {
        Long id = started();
        Long other = session(r -> {
            r.setTitle("Safety Refresher");
            r.setStartTime(LocalTime.of(14, 0));
            r.setEndTime(LocalTime.of(16, 0));
            r.setDate(today.plusDays(8));
        });
        TrainingDownload otherTemplate = attendance.hrdcTemplate(other, asWeiMing);

        refused("This file is for another session: \"Safety Refresher\" on Tue 20 Oct 2026. Download this session's "
                + "HRDC template and fill that in.", () -> attendance.uploadBatch(id, filled(otherTemplate, Map.of()), null, asWeiMing));
        refused("This is an old Excel file (.xls). Save it as an Excel workbook (.xlsx) or CSV, then upload it again.",
                () -> attendance.uploadBatch(id, file("old.xls", new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 1}),
                        null, asWeiMing));
        refused("This Excel file can't be read. Save it again as an Excel workbook (.xlsx), or download a fresh template.",
                () -> attendance.uploadBatch(id, file("broken.xlsx", new byte[]{'P', 'K', 3, 4, 9, 9, 9}), null, asWeiMing));
        refused("This file has no Attended column. Download the HRDC template, fill it in, and upload that.",
                () -> attendance.uploadBatch(id, csv("Name,Present\nNur Aisyah,Y\n"), null, asWeiMing));
        refused("This file has no Email or Full name column, so its rows can't be matched to people. Download the HRDC "
                + "template and fill that in.", () -> attendance.uploadBatch(id, csv("Attended\nY\n"), null, asWeiMing));
        refused("The file has no attendance rows. Fill in Attended for each person, then upload it.",
                () -> attendance.uploadBatch(id, csv("Email,Attended\n,\n"), null, asWeiMing));
        refused("Choose the filled-in attendance file to upload.",
                () -> attendance.uploadBatch(id, file("empty.csv", new byte[0]), null, asWeiMing));
        refused("The attendance file can be up to 2 MB.",
                () -> attendance.uploadBatch(id, file("big.csv", new byte[2 * 1024 * 1024 + 1]), null, asWeiMing));
        assertTrue(store.attendance.isEmpty());
    }

    @Test
    @DisplayName("Uploads follow the attendance rules: from the start, the trainer for 7 days, then HR with a logged reason")
    void whoAndWhen() {
        Long id = session(r -> { });
        register(id, asAisyah, "010203-10-1234");
        MockMultipartFile marks = csv("Email,Attended\nnur.aisyah@tbm.net,Y\n");

        refused("Attendance can be recorded once the session starts, at Tue 20 Oct, 09:00.",
                () -> attendance.uploadBatch(id, marks, null, asWeiMing));
        at(today.plusDays(8), 9, 0);
        assertThrows(ForbiddenRequestException.class, () -> attendance.uploadBatch(id, marks, null, asKarim));
        assertThrows(ForbiddenRequestException.class, () -> attendance.uploadBatch(id, marks, null, asAisyah));

        at(today.plusDays(15), 12, 1); // the trainer's 7 days ended at 12:00
        refused("The 7 days for recording attendance have passed. Only HR can record it now.",
                () -> attendance.uploadBatch(id, marks, null, asWeiMing));
        refused("Give a reason for the change. It is logged.", () -> attendance.uploadBatch(id, marks, " ", asHr));
        TrainingBatchResultDto result = attendance.uploadBatch(id, marks, "Sign-in sheet arrived late", asHr);

        assertEquals("ATTENDED", row(result.getSheet(), aisyah).getStatus());
        TrainingAuditLog logged = audits(TrainingAuditAction.ATTENDANCE_CHANGED).get(0);
        assertEquals("Sign-in sheet arrived late", logged.getReason());
        assertEquals("From an uploaded attendance file: Nur Aisyah: Not recorded to Attended", logged.getDetail());
    }
}
