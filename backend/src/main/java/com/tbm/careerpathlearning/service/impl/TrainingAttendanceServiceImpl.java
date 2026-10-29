package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrainingAttendanceRequest;
import com.tbm.careerpathlearning.dto.TrainingAttendanceSheetDto;
import com.tbm.careerpathlearning.dto.TrainingBatchResultDto;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.enums.TrainingAttendanceMethod;
import com.tbm.careerpathlearning.enums.TrainingAttendanceStatus;
import com.tbm.careerpathlearning.enums.TrainingAuditAction;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.enums.TrainingRegistrationStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAttendance;
import com.tbm.careerpathlearning.model.TrainingEvidence;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.model.TrainingRegistration;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingAttendanceRepository;
import com.tbm.careerpathlearning.repository.TrainingEvidenceRepository;
import com.tbm.careerpathlearning.repository.TrainingProgramRepository;
import com.tbm.careerpathlearning.repository.TrainingRegistrationRepository;
import com.tbm.careerpathlearning.service.FileService;
import com.tbm.careerpathlearning.service.TrainingAttendanceService;
import com.tbm.careerpathlearning.service.TrainingAuditService;
import com.tbm.careerpathlearning.service.TrainingDownload;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TrainingAttendanceServiceImpl implements TrainingAttendanceService {

    /** How long after a session its trainer may still record attendance (FR-14-01). */
    static final Duration TRAINER_WINDOW = Duration.ofDays(7);
    /** Evidence per session (decision B, 28 Oct 2026). */
    static final int MAX_EVIDENCE_FILES = 5;
    static final long MAX_EVIDENCE_BYTES = 10L * 1024 * 1024;
    private static final String EVIDENCE_FOLDER = "training-evidence";
    private static final int MAX_REASON = 500;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);

    @Autowired private TrainingProgramRepository trainingProgramRepository;
    @Autowired private TrainingRegistrationRepository trainingRegistrationRepository;
    @Autowired private TrainingAttendanceRepository trainingAttendanceRepository;
    @Autowired private TrainingEvidenceRepository trainingEvidenceRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private TrainingAuditService trainingAuditService;
    @Autowired private FileService fileService;
    /** Malaysia time, whatever time zone the server runs in. */
    @Autowired private Clock clock;

    @Override
    @Transactional(readOnly = true)
    public TrainingAttendanceSheetDto sheet(Long sessionId, TrainingViewer viewer) {
        TrainingProgram s = find(sessionId);
        if (!recorder(s, viewer) && !viewer.superAdmin()) {
            throw new ForbiddenRequestException("Only the session's trainer, HR and the Super Admin can see its attendance.");
        }
        return sheetOf(s, viewer);
    }

    @Override
    @Transactional
    public TrainingAttendanceSheetDto record(Long sessionId, TrainingAttendanceRequest request, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = locked(sessionId);
        boolean logged = writable(s, viewer, now, request.getReason());
        List<TrainingAttendanceRequest.Entry> entries = request.getEntries() == null ? List.of()
                : request.getEntries().stream().filter(Objects::nonNull).toList();
        if (entries.isEmpty()) {
            throw new BadRequestException("Choose who to mark.");
        }
        Map<UUID, TrainingAttendance> rows = rowsBySession(sessionId);
        Set<UUID> onSheet = new HashSet<>(registered(sessionId));
        onSheet.addAll(rows.keySet());
        Set<UUID> named = new HashSet<>(onSheet);
        entries.stream().map(TrainingAttendanceRequest.Entry::getStaffId).filter(Objects::nonNull).forEach(named::add);
        Map<UUID, String> names = names(named);

        // Every entry is checked before any is saved, so a request is applied whole or not at all.
        Set<UUID> seen = new HashSet<>();
        for (TrainingAttendanceRequest.Entry entry : entries) {
            UUID staffId = entry.getStaffId();
            String name = staffId == null ? "Someone" : names.getOrDefault(staffId, "Someone");
            if (staffId == null || !onSheet.contains(staffId)) {
                throw new BadRequestException(name + " isn't on this session. Add them as a walk-in first.");
            }
            if (!seen.add(staffId)) {
                throw new BadRequestException(name + " is marked twice. Mark each person once.");
            }
            Integer score = entry.getQuizScore();
            if (score != null && (score < 0 || score > 100)) {
                throw new BadRequestException("Quiz scores are from 0 to 100.");
            }
            if (score != null && entry.getStatus() != TrainingAttendanceStatus.ATTENDED) {
                throw new BadRequestException("A quiz score goes with Attended only.");
            }
        }

        List<String> changes = new ArrayList<>();
        for (TrainingAttendanceRequest.Entry entry : entries) {
            UUID staffId = entry.getStaffId();
            String name = names.getOrDefault(staffId, "Unknown user");
            TrainingAttendance row = rows.get(staffId);
            String before = label(row);
            if (entry.getStatus() == null) {
                if (row != null) {
                    trainingAttendanceRepository.delete(row);
                    changes.add(name + ": " + before + " to Not recorded");
                }
                continue;
            }
            Integer score = entry.getQuizScore();
            if (row != null && row.getStatus() == entry.getStatus() && Objects.equals(row.getQuizScore(), score)) {
                continue;
            }
            if (row == null) {
                row = new TrainingAttendance();
                row.setTrainingId(sessionId);
                row.setStaffId(staffId);
            }
            row.setStatus(entry.getStatus());
            row.setQuizScore(score);
            row.setMethod(TrainingAttendanceMethod.MANUAL);
            row.setRecordedBy(viewer.userId());
            row.setRecordedAt(now);
            trainingAttendanceRepository.save(row);
            changes.add(name + ": " + before + " to " + label(row));
        }
        if (logged && !changes.isEmpty()) {
            audit(viewer, TrainingAuditAction.ATTENDANCE_CHANGED, s, request.getReason().trim(), String.join("; ", changes));
        }
        return sheetOf(s, viewer);
    }

    @Override
    @Transactional
    public TrainingAttendanceSheetDto addWalkIn(Long sessionId, UUID staffId, String reason, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = locked(sessionId);
        boolean logged = writable(s, viewer, now, reason);
        Staff person = staffId == null ? null : staffRepository.findAllWithRoleByIdIn(List.of(staffId)).stream()
                .filter(x -> !x.isDeleted() && x.getAccountStatus() == StaffAccountStatus.ACTIVE).findFirst().orElse(null);
        if (person == null) {
            throw new BadRequestException("Choose an active staff member.");
        }
        if (person.getId().equals(s.getTrainerId())) {
            throw new BadRequestException(person.getName() + " runs this session.");
        }
        if (registered(sessionId).contains(person.getId()) || rowsBySession(sessionId).containsKey(person.getId())) {
            throw new BadRequestException(person.getName() + " is already on the attendance list.");
        }
        TrainingAttendance row = new TrainingAttendance();
        row.setTrainingId(sessionId);
        row.setStaffId(person.getId());
        row.setStatus(TrainingAttendanceStatus.ATTENDED);
        row.setWalkIn(true);
        row.setMethod(TrainingAttendanceMethod.MANUAL);
        row.setRecordedBy(viewer.userId());
        row.setRecordedAt(now);
        trainingAttendanceRepository.save(row);
        if (logged) {
            audit(viewer, TrainingAuditAction.ATTENDANCE_CHANGED, s, reason.trim(), person.getName() + ": walk-in, Attended");
        }
        return sheetOf(s, viewer);
    }

    @Override
    @Transactional
    public TrainingAttendanceSheetDto submit(Long sessionId, String reason, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = locked(sessionId);
        if (s.getAttendanceSubmittedAt() != null) {
            throw new BadRequestException("Attendance has already been submitted.");
        }
        boolean logged = writable(s, viewer, now, reason);
        TrainingAttendanceSheetDto sheet = sheetOf(s, viewer);
        if (sheet.getRows().isEmpty()) {
            throw new BadRequestException("Nobody is on this session, so there is no attendance to submit.");
        }
        List<String> unmarked = sheet.getRows().stream().filter(r -> "NOT_RECORDED".equals(r.getStatus()))
                .map(TrainingAttendanceSheetDto.Row::getName).toList();
        if (!unmarked.isEmpty()) { // decision C, 28 Oct 2026
            throw new BadRequestException("Mark everyone first: " + listed(unmarked)
                    + (unmarked.size() == 1 ? " is" : " are") + " still Not recorded.");
        }
        s.setAttendanceSubmittedAt(now);
        s.setAttendanceSubmittedBy(viewer.userId());
        audit(viewer, TrainingAuditAction.ATTENDANCE_SUBMITTED, s, logged ? reason.trim() : null,
                sheet.getAttended() + " attended, " + sheet.getAbsent() + " absent");
        return sheetOf(s, viewer);
    }

    @Override
    @Transactional
    public TrainingAttendanceSheetDto addEvidence(Long sessionId, MultipartFile file, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = locked(sessionId);
        evidenceWritable(s, viewer, now);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a photo or PDF to attach.");
        }
        if (file.getSize() > MAX_EVIDENCE_BYTES) {
            throw new BadRequestException("Evidence files can be up to 10 MB.");
        }
        if (trainingEvidenceRepository.findAllByTrainingIdOrderByUploadedAtAscEvidenceIdAsc(sessionId).size() >= MAX_EVIDENCE_FILES) {
            throw new BadRequestException("A session can have up to " + MAX_EVIDENCE_FILES + " evidence files. Remove one first.");
        }
        String type = contentType(file);
        if (type == null) {
            throw new BadRequestException("Attach a photo (JPG or PNG) or a PDF.");
        }
        String extension = switch (type) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "pdf";
        };
        // A generated name: the person's own file name is only shown, never used as a path.
        String stored = EVIDENCE_FOLDER + "/" + sessionId + "/" + UUID.randomUUID() + "." + extension;
        try (InputStream in = file.getInputStream()) {
            Path target = fileService.getFilePath(stored);
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store the evidence file", e);
        }
        TrainingEvidence evidence = new TrainingEvidence();
        evidence.setTrainingId(sessionId);
        evidence.setFileName(displayName(file.getOriginalFilename(), extension));
        evidence.setStoredPath(stored);
        evidence.setContentType(type);
        evidence.setSizeBytes(file.getSize());
        evidence.setUploadedBy(viewer.userId());
        evidence.setUploadedAt(now);
        trainingEvidenceRepository.save(evidence);
        return sheetOf(s, viewer);
    }

    @Override
    @Transactional
    public TrainingAttendanceSheetDto removeEvidence(Long sessionId, Long evidenceId, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = locked(sessionId);
        evidenceWritable(s, viewer, now);
        TrainingEvidence evidence = evidenceOf(sessionId, evidenceId);
        fileService.deleteFile(evidence.getStoredPath());
        trainingEvidenceRepository.delete(evidence);
        audit(viewer, TrainingAuditAction.EVIDENCE_REMOVED, s, null, evidence.getFileName());
        return sheetOf(s, viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public EvidenceFile evidenceFile(Long sessionId, Long evidenceId, TrainingViewer viewer) {
        TrainingProgram s = find(sessionId);
        if (!recorder(s, viewer) && !viewer.superAdmin()) {
            throw new ForbiddenRequestException("Only the session's trainer, HR and the Super Admin can see its evidence.");
        }
        TrainingEvidence evidence = evidenceOf(sessionId, evidenceId);
        Path path = fileService.getFilePath(evidence.getStoredPath());
        if (!Files.exists(path)) {
            throw new BadRequestException("That file is no longer stored.");
        }
        return new EvidenceFile(path, evidence.getFileName(), evidence.getContentType());
    }

    @Override
    @Transactional
    public TrainingDownload hrdcTemplate(Long sessionId, TrainingViewer viewer) {
        TrainingProgram s = find(sessionId);
        if (!recorder(s, viewer)) {
            throw new ForbiddenRequestException("Only the session's trainer, and HR, record its attendance.");
        }
        if (s.getStatus() == TrainingSessionStatus.CANCELLED) {
            throw new BadRequestException("This session was cancelled, so it has no attendance.");
        }
        Map<UUID, TrainingAttendance> rows = rowsBySession(sessionId);
        Map<UUID, TrainingRegistration> forms = new HashMap<>();
        trainingRegistrationRepository.findAllByTrainingId(sessionId).stream()
                .filter(r -> r.getStatus() == TrainingRegistrationStatus.REGISTERED)
                .forEach(r -> forms.put(r.getStaffId(), r));
        Set<UUID> people = new HashSet<>(forms.keySet());
        people.addAll(rows.keySet());
        Set<UUID> lookup = new HashSet<>(people);
        if (s.getTrainerId() != null) lookup.add(s.getTrainerId());
        Map<UUID, Staff> staff = lookup.isEmpty() ? Map.of() : staffRepository.findAllWithRoleByIdIn(lookup).stream()
                .collect(Collectors.toMap(Staff::getId, x -> x));
        boolean exported = false;
        byte[] content;
        try (TrainingSpreadsheets.Book book = new TrainingSpreadsheets.Book()) {
            Sheet sheet = book.sheet("Attendance", 30, 26, 20, 22, 22, 26, 14, 16);
            String trainer = s.getTrainerId() == null ? s.getTrainerName() : nameOf(staff, s.getTrainerId());
            book.row(sheet, 0, book.title, "HRDC attendance");
            book.row(sheet, 1, book.label, s.getTitle());
            book.row(sheet, 2, null, TrainingSpreadsheets.when(s) + (s.getVenue() == null ? "" : " · " + s.getVenue())
                    + " · Trainer: " + Objects.toString(trainer, "not set"));
            book.row(sheet, 3, book.note, "Fill in Attended with Y or N, and the quiz score (0 to 100) if there was a "
                    + "quiz. Rows left blank stay as they are. Keep the emails: they match each row to a person.");
            book.row(sheet, HRDC_HEADER_ROW, book.header, (Object[]) HRDC_COLUMNS);
            int r = HRDC_HEADER_ROW + 1;
            for (UUID id : byName(people, staff)) {
                Staff person = staff.get(id);
                TrainingRegistration form = forms.get(id);
                TrainingAttendance mark = rows.get(id);
                OrgChart department = person == null ? null : SopAssignmentServiceImpl.department(person);
                String idNumber = form == null ? null : form.getIdNumber();
                exported |= idNumber != null;
                Row row = book.row(sheet, r++, null,
                        person == null ? null : person.getEmail(),
                        person == null ? "Unknown user" : person.getName(),
                        idNumber,
                        person == null || person.getRole() == null ? null : person.getRole().getName(),
                        department == null ? null : department.getName(),
                        form == null ? null : form.getBranch(),
                        mark == null ? null : mark.getStatus() == TrainingAttendanceStatus.ATTENDED ? "Y" : "N",
                        mark == null ? null : mark.getQuizScore());
                row.getCell(7).setCellStyle(book.plain);
            }
            markCells(sheet, HRDC_HEADER_ROW + 1, Math.max(r, HRDC_HEADER_ROW + 1) + 50);
            sheet.createFreezePane(0, HRDC_HEADER_ROW + 1);
            Sheet about = book.sheet(TrainingSpreadsheets.ABOUT_SHEET, 12, 40);
            book.row(about, 0, null, "Session", String.valueOf(sessionId));
            book.row(about, 1, null, "Title", s.getTitle());
            book.row(about, 2, null, "Note", "Matches this file to its session. Don't change it.");
            book.workbook.setSheetHidden(book.workbook.getSheetIndex(about), true);
            content = book.bytes();
        }
        if (exported) {
            audit(viewer, TrainingAuditAction.IC_NUMBERS_EXPORTED, s, null, "HRDC attendance template with IC numbers");
        }
        return new TrainingDownload(content, TrainingSpreadsheets.fileName("HRDC attendance", s), TrainingSpreadsheets.XLSX);
    }

    @Override
    @Transactional
    public TrainingBatchResultDto uploadBatch(Long sessionId, MultipartFile file, String reason, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        TrainingProgram s = locked(sessionId);
        boolean logged = writable(s, viewer, now, reason);
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose the filled-in attendance file to upload.");
        }
        if (file.getSize() > TrainingSpreadsheets.MAX_UPLOAD_BYTES) {
            throw new BadRequestException("The attendance file can be up to 2 MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("The file couldn't be read. Upload it again.");
        }
        TrainingSpreadsheets.Upload upload = TrainingSpreadsheets.read(bytes);
        if (upload.sessionKey() != null && !upload.sessionKey().equals(String.valueOf(sessionId))) {
            throw new BadRequestException(otherSession(upload.sessionKey()));
        }
        List<BatchLine> lines = batchLines(upload.rows());
        if (lines.isEmpty()) {
            throw new BadRequestException("The file has no attendance rows. Fill in Attended for each person, then upload it.");
        }

        Map<UUID, TrainingAttendance> rows = rowsBySession(sessionId);
        Set<UUID> onSheet = new HashSet<>(registered(sessionId));
        onSheet.addAll(rows.keySet());
        Map<UUID, Staff> people = onSheet.isEmpty() ? Map.of() : staffRepository.findAllWithRoleByIdIn(onSheet).stream()
                .collect(Collectors.toMap(Staff::getId, x -> x));
        Map<String, UUID> byEmail = new HashMap<>();
        Map<String, List<UUID>> byName = new HashMap<>();
        people.values().forEach(p -> {
            if (p.getEmail() != null) byEmail.put(lower(p.getEmail()), p.getId());
            byName.computeIfAbsent(nameKey(p.getName()), k -> new ArrayList<>()).add(p.getId());
        });

        // Every row is checked before any mark is saved, so a file is applied whole or not at all.
        TrainingBatchResultDto result = new TrainingBatchResultDto();
        result.setRows(lines.size());
        List<String> problems = new ArrayList<>();
        Map<UUID, Integer> rowOf = new HashMap<>();
        List<TrainingAttendanceRequest.Entry> marks = new ArrayList<>();
        for (BatchLine line : lines) {
            String attendedText = line.attended().trim();
            TrainingAttendanceStatus status = attendedText.isEmpty() ? null : mark(attendedText);
            boolean valid = attendedText.isEmpty() || status != null;
            if (!valid) {
                problems.add("row " + line.row() + ": Attended must be Y or N, not \"" + shortened(attendedText) + "\"");
            }
            String scoreText = line.score().trim();
            Integer score = scoreText.isEmpty() ? null : score(scoreText);
            if (!scoreText.isEmpty()) {
                if (score == null) {
                    problems.add("row " + line.row() + ": the quiz score must be a whole number from 0 to 100, not \""
                            + shortened(scoreText) + "\"");
                    valid = false;
                } else if (attendedText.isEmpty()) {
                    problems.add("row " + line.row() + ": fill in Attended (Y) to go with the quiz score");
                    valid = false;
                } else if (status == TrainingAttendanceStatus.ABSENT) {
                    problems.add("row " + line.row() + ": a quiz score goes with Attended (Y) only");
                    valid = false;
                }
            }
            String email = lower(line.email());
            UUID who = email.isEmpty() ? null : byEmail.get(email);
            List<UUID> named = byName.getOrDefault(nameKey(line.name()), List.of());
            if (who == null && named.size() == 1 && !line.name().isBlank()) {
                who = named.get(0);
            }
            if (who == null) {
                String label = line.name().isBlank() ? line.email().trim() : line.name().trim();
                if (!label.isEmpty()) { // decision B2: listed, never added
                    result.getUnmatched().add("Row " + line.row() + ", " + shortened(label) + ": "
                            + (named.size() > 1 ? "more than one person on this session has this name, so keep their email"
                            : "not on this session"));
                }
                continue;
            }
            Integer earlier = rowOf.putIfAbsent(who, line.row());
            if (earlier != null) {
                problems.add("rows " + earlier + " and " + line.row() + " are both "
                        + people.get(who).getName() + ", so keep one");
                continue;
            }
            result.setMatched(result.getMatched() + 1);
            if (!valid) continue;
            if (status == null) { // decision B1: a blank Attended changes nothing
                result.setLeftBlank(result.getLeftBlank() + 1);
                continue;
            }
            if (status == TrainingAttendanceStatus.ATTENDED) result.setAttended(result.getAttended() + 1);
            else result.setAbsent(result.getAbsent() + 1);
            TrainingAttendanceRequest.Entry entry = new TrainingAttendanceRequest.Entry();
            entry.setStaffId(who);
            entry.setStatus(status);
            entry.setQuizScore(score);
            marks.add(entry);
        }
        if (!problems.isEmpty()) {
            int more = problems.size() - 5;
            throw new BadRequestException("Nothing was saved. Fix the file, then upload it again: "
                    + String.join("; ", problems.subList(0, Math.min(5, problems.size())))
                    + (more > 0 ? "; and " + more + " more" : "") + ".");
        }

        List<String> changes = new ArrayList<>();
        for (TrainingAttendanceRequest.Entry entry : marks) {
            TrainingAttendance row = rows.get(entry.getStaffId());
            if (row != null && row.getStatus() == entry.getStatus() && Objects.equals(row.getQuizScore(), entry.getQuizScore())) {
                continue;
            }
            String before = label(row);
            if (row == null) {
                row = new TrainingAttendance();
                row.setTrainingId(sessionId);
                row.setStaffId(entry.getStaffId());
            }
            row.setStatus(entry.getStatus());
            row.setQuizScore(entry.getQuizScore());
            row.setMethod(TrainingAttendanceMethod.BATCH);
            row.setRecordedBy(viewer.userId());
            row.setRecordedAt(now);
            trainingAttendanceRepository.save(row);
            changes.add(people.get(entry.getStaffId()).getName() + ": " + before + " to " + label(row));
        }
        result.setChanged(changes.size());
        if (logged && !changes.isEmpty()) {
            audit(viewer, TrainingAuditAction.ATTENDANCE_CHANGED, s, reason.trim(),
                    "From an uploaded attendance file: " + String.join("; ", changes));
        }
        result.setSheet(sheetOf(s, viewer));
        return result;
    }

    // --- the HRDC attendance file -----------------------------------------------------------

    /** The template's columns (FR-14-05); an upload finds its columns by these headings. */
    static final String[] HRDC_COLUMNS = {"Email", "Full name", "IC or passport no.", "Job role", "Department", "Branch",
            "Attended (Y/N)", "Quiz score (0-100)"};
    private static final int HRDC_HEADER_ROW = 5;
    private static final int ATTENDED_COLUMN = 6;
    private static final int SCORE_COLUMN = 7;

    /** One attendance row of an uploaded file, numbered as the spreadsheet numbers it. */
    private record BatchLine(int row, String email, String name, String attended, String score) {
    }

    /** Y or N picked from a list, and whole-number scores, as the trainer fills them in. */
    private static void markCells(Sheet sheet, int first, int last) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidation attended = helper.createValidation(helper.createExplicitListConstraint(new String[]{"Y", "N"}),
                new CellRangeAddressList(first, last, ATTENDED_COLUMN, ATTENDED_COLUMN));
        attended.setShowErrorBox(true);
        attended.createErrorBox("Attended", "Enter Y if they attended, or N if they didn't.");
        sheet.addValidationData(attended);
        DataValidation score = helper.createValidation(helper.createIntegerConstraint(
                        DataValidationConstraint.OperatorType.BETWEEN, "0", "100"),
                new CellRangeAddressList(first, last, SCORE_COLUMN, SCORE_COLUMN));
        score.setShowErrorBox(true);
        score.createErrorBox("Quiz score", "Enter a whole number from 0 to 100, or leave it empty.");
        sheet.addValidationData(score);
    }

    /** The rows under the heading row, which is found by its Attended column; empty rows are skipped. */
    private static List<BatchLine> batchLines(List<List<String>> rows) {
        int header = -1;
        int email = -1, name = -1, attended = -1, score = -1;
        for (int i = 0; i < Math.min(rows.size(), 15) && header < 0; i++) {
            List<String> cells = rows.get(i);
            for (int j = 0; j < cells.size(); j++) {
                String h = lower(cells.get(j));
                if (h.startsWith("attended") || h.equals("attendance")) {
                    header = i;
                    attended = j;
                }
            }
            if (header < 0) continue;
            for (int j = 0; j < cells.size(); j++) {
                String h = lower(cells.get(j));
                if (j == attended) continue;
                if (email < 0 && (h.contains("email") || h.contains("e-mail"))) email = j;
                else if (name < 0 && h.contains("name")) name = j;
                else if (score < 0 && (h.contains("quiz") || h.contains("score"))) score = j;
            }
        }
        if (header < 0) {
            throw new BadRequestException("This file has no Attended column. Download the HRDC template, fill it in, "
                    + "and upload that.");
        }
        if (email < 0 && name < 0) {
            throw new BadRequestException("This file has no Email or Full name column, so its rows can't be matched to "
                    + "people. Download the HRDC template and fill that in.");
        }
        List<BatchLine> lines = new ArrayList<>();
        for (int i = header + 1; i < rows.size(); i++) {
            List<String> cells = rows.get(i);
            BatchLine line = new BatchLine(i + 1, cell(cells, email), cell(cells, name), cell(cells, attended), cell(cells, score));
            if (!(line.email().isBlank() && line.name().isBlank() && line.attended().isBlank() && line.score().isBlank())) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String cell(List<String> cells, int column) {
        return column < 0 || column >= cells.size() || cells.get(column) == null ? "" : cells.get(column);
    }

    /** Y or N as people write them, in English or Malay; null for anything else. */
    private static TrainingAttendanceStatus mark(String text) {
        return switch (text.trim().toUpperCase(Locale.ROOT)) {
            case "Y", "YES", "1", "TRUE", "ATTENDED", "PRESENT", "HADIR" -> TrainingAttendanceStatus.ATTENDED;
            case "N", "NO", "0", "FALSE", "ABSENT", "TIDAK", "TIDAK HADIR" -> TrainingAttendanceStatus.ABSENT;
            default -> null;
        };
    }

    /** A whole number from 0 to 100, with or without "%"; null for anything else. */
    private static Integer score(String text) {
        String t = text.trim();
        if (t.endsWith("%")) t = t.substring(0, t.length() - 1).trim();
        try {
            int value = new BigDecimal(t).intValueExact();
            return value >= 0 && value <= 100 ? value : null;
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private String otherSession(String key) {
        String session = "";
        try {
            session = trainingProgramRepository.findByTrainingIdAndDeletedFalse(Long.valueOf(key))
                    .map(o -> ": \"" + o.getTitle() + "\" on " + o.getSessionDate().format(TrainingSpreadsheets.DAY))
                    .orElse("");
        } catch (NumberFormatException ignored) {
            // an About sheet someone typed over: still another session's file
        }
        return "This file is for another session" + session + ". Download this session's HRDC template and fill that in.";
    }

    private static String lower(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    /** Names compared without case or extra spaces. */
    private static String nameKey(String name) {
        return lower(name).replaceAll("\\s+", " ");
    }

    private static String shortened(String text) {
        return text.length() > 40 ? text.substring(0, 40) + "…" : text;
    }

    private static List<UUID> byName(Set<UUID> ids, Map<UUID, Staff> staff) {
        return ids.stream().sorted(Comparator.comparing((UUID id) -> staff.get(id) == null ? "" : Objects.toString(
                staff.get(id).getName(), ""), String.CASE_INSENSITIVE_ORDER)).toList();
    }

    // --- rules ---------------------------------------------------------------------------

    /** The session's trainer (whoever scheduled it, or runs it) and HR record its attendance (FR-14-01). */
    static boolean recorder(TrainingProgram s, TrainingViewer viewer) {
        return TrainingServiceImpl.canManage(s, viewer) || viewer.userId().equals(s.getTrainerId());
    }

    static LocalDateTime trainerUntil(TrainingProgram s) {
        return s.endsAt().plus(TRAINER_WINDOW);
    }

    /**
     * Whether the viewer may change the attendance now. From the start until 7 days after, the trainer and HR may;
     * after that, or once it is submitted, only HR, giving a reason (FR-14-01, FR-14-09).
     *
     * @return true when the change is HR's late one, to be logged with its reason
     */
    private boolean writable(TrainingProgram s, TrainingViewer viewer, LocalDateTime now, String reason) {
        open(s, viewer, now);
        boolean submitted = s.getAttendanceSubmittedAt() != null;
        boolean late = now.isAfter(trainerUntil(s));
        if (!submitted && !late) {
            return false;
        }
        if (!viewer.hr()) {
            throw new BadRequestException(submitted ? "Attendance has been submitted. Only HR can change it now."
                    : "The 7 days for recording attendance have passed. Only HR can record it now.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Give a reason for the change. It is logged.");
        }
        if (reason.trim().length() > MAX_REASON) {
            throw new BadRequestException("Keep the reason to " + MAX_REASON + " characters.");
        }
        return true;
    }

    /** Evidence can be added or removed while attendance can be recorded; afterwards by HR (FR-14-08). */
    private void evidenceWritable(TrainingProgram s, TrainingViewer viewer, LocalDateTime now) {
        open(s, viewer, now);
        boolean closed = s.getAttendanceSubmittedAt() != null || now.isAfter(trainerUntil(s));
        if (closed && !viewer.hr()) {
            throw new BadRequestException("Attendance is closed for the trainer. Only HR can change its evidence now.");
        }
    }

    private static void open(TrainingProgram s, TrainingViewer viewer, LocalDateTime now) {
        if (!recorder(s, viewer)) {
            throw new ForbiddenRequestException("Only the session's trainer, and HR, record its attendance.");
        }
        if (s.getStatus() == TrainingSessionStatus.CANCELLED) {
            throw new BadRequestException("This session was cancelled, so it has no attendance.");
        }
        if (s.getStatus() == TrainingSessionStatus.ARCHIVED) {
            throw new BadRequestException("This session is archived. Restore it to change its attendance.");
        }
        if (now.isBefore(s.startsAt())) {
            throw new BadRequestException("Attendance can be recorded once the session starts, at "
                    + s.startsAt().format(TIME) + ".");
        }
    }

    // --- the sheet ----------------------------------------------------------------------------

    private TrainingAttendanceSheetDto sheetOf(TrainingProgram s, TrainingViewer viewer) {
        LocalDateTime now = LocalDateTime.now(clock);
        Long id = s.getTrainingId();
        Map<UUID, TrainingAttendance> rows = rowsBySession(id);
        Map<UUID, TrainingRegistration> forms = new HashMap<>();
        trainingRegistrationRepository.findAllByTrainingId(id).stream()
                .filter(r -> r.getStatus() == TrainingRegistrationStatus.REGISTERED)
                .forEach(r -> forms.put(r.getStaffId(), r));
        Set<UUID> people = new HashSet<>(forms.keySet());
        people.addAll(rows.keySet());
        List<TrainingEvidence> evidence = trainingEvidenceRepository.findAllByTrainingIdOrderByUploadedAtAscEvidenceIdAsc(id);
        // Whoever records it checks each person against their IC or passport number (29 Oct 2026).
        boolean recorder = recorder(s, viewer);
        Set<UUID> lookup = new HashSet<>(people);
        rows.values().stream().map(TrainingAttendance::getRecordedBy).filter(Objects::nonNull).forEach(lookup::add);
        evidence.forEach(e -> lookup.add(e.getUploadedBy()));
        if (s.getAttendanceSubmittedBy() != null) {
            lookup.add(s.getAttendanceSubmittedBy());
        }
        Map<UUID, Staff> staff = lookup.isEmpty() ? Map.of() : staffRepository.findAllWithRoleByIdIn(lookup).stream()
                .collect(Collectors.toMap(Staff::getId, x -> x));

        TrainingAttendanceSheetDto dto = new TrainingAttendanceSheetDto();
        dto.setSessionId(id);
        for (UUID staffId : people) {
            Staff person = staff.get(staffId);
            TrainingAttendance row = rows.get(staffId);
            TrainingAttendanceSheetDto.Row r = new TrainingAttendanceSheetDto.Row();
            r.setStaffId(staffId);
            r.setName(person == null ? "Unknown user" : person.getName());
            if (person != null) {
                OrgChart department = SopAssignmentServiceImpl.department(person);
                r.setRoleName(person.getRole() == null ? null : person.getRole().getName());
                r.setDepartmentName(department == null ? null : department.getName());
            }
            TrainingRegistration form = forms.get(staffId);
            if (recorder && form != null) {
                r.setIdNumber(form.getIdNumber());
            }
            r.setStatus(row == null ? "NOT_RECORDED" : row.getStatus().name());
            if (row != null) {
                r.setQuizScore(row.getQuizScore());
                r.setWalkIn(row.isWalkIn());
                r.setMethod(row.getMethod().name());
                r.setRecordedByName(nameOf(staff, row.getRecordedBy()));
                r.setRecordedAt(row.getRecordedAt());
            }
            dto.getRows().add(r);
        }
        dto.getRows().sort(Comparator.comparing(r -> Objects.toString(r.getName(), ""), String.CASE_INSENSITIVE_ORDER));
        dto.setAttended((int) dto.getRows().stream().filter(r -> "ATTENDED".equals(r.getStatus())).count());
        dto.setAbsent((int) dto.getRows().stream().filter(r -> "ABSENT".equals(r.getStatus())).count());
        dto.setNotRecorded(dto.getRows().size() - dto.getAttended() - dto.getAbsent());

        boolean scheduled = s.getStatus() == TrainingSessionStatus.SCHEDULED;
        boolean started = !now.isBefore(s.startsAt());
        boolean submitted = s.getAttendanceSubmittedAt() != null;
        boolean late = now.isAfter(trainerUntil(s));
        dto.setOpensAt(s.startsAt());
        dto.setTrainerUntil(trainerUntil(s));
        dto.setSubmitted(submitted);
        dto.setSubmittedAt(s.getAttendanceSubmittedAt());
        dto.setSubmittedByName(submitted ? nameOf(staff, s.getAttendanceSubmittedBy()) : null);
        dto.setCanRecord(recorder && scheduled && started && !submitted && !late);
        dto.setHrOnly(scheduled && started && (submitted || late));
        dto.setCanChangeWithReason(dto.isHrOnly() && recorder && viewer.hr());
        dto.setCanSubmit(!submitted && !dto.getRows().isEmpty() && dto.getNotRecorded() == 0
                && (dto.isCanRecord() || dto.isCanChangeWithReason()));
        for (TrainingEvidence e : evidence) {
            TrainingAttendanceSheetDto.Evidence ev = new TrainingAttendanceSheetDto.Evidence();
            ev.setId(e.getEvidenceId());
            ev.setFileName(e.getFileName());
            ev.setContentType(e.getContentType());
            ev.setSizeBytes(e.getSizeBytes());
            ev.setUploadedByName(nameOf(staff, e.getUploadedBy()));
            ev.setUploadedAt(e.getUploadedAt());
            dto.getEvidence().add(ev);
        }
        return dto;
    }

    // --- helpers ------------------------------------------------------------------------------

    private Set<UUID> registered(Long sessionId) {
        return trainingRegistrationRepository.findAllByTrainingId(sessionId).stream()
                .filter(r -> r.getStatus() == TrainingRegistrationStatus.REGISTERED)
                .map(TrainingRegistration::getStaffId).collect(Collectors.toSet());
    }

    private Map<UUID, TrainingAttendance> rowsBySession(Long sessionId) {
        Map<UUID, TrainingAttendance> rows = new LinkedHashMap<>();
        trainingAttendanceRepository.findAllByTrainingId(sessionId).forEach(r -> rows.put(r.getStaffId(), r));
        return rows;
    }

    private Map<UUID, String> names(Set<UUID> ids) {
        return ids.isEmpty() ? Map.of() : staffRepository.findAllWithRoleByIdIn(ids).stream()
                .collect(Collectors.toMap(Staff::getId, s -> Objects.toString(s.getName(), "Unknown user")));
    }

    private static String nameOf(Map<UUID, Staff> staff, UUID id) {
        Staff s = id == null ? null : staff.get(id);
        return s == null ? (id == null ? null : "Unknown user") : s.getName();
    }

    private static String label(TrainingAttendance row) {
        if (row == null) return "Not recorded";
        String status = row.getStatus() == TrainingAttendanceStatus.ATTENDED ? "Attended" : "Absent";
        return row.getQuizScore() == null ? status : status + " (quiz " + row.getQuizScore() + "%)";
    }

    /** "a", "a and b", "a, b and c", or "a, b, c and 2 others". */
    private static String listed(List<String> names) {
        if (names.size() > 3) {
            return String.join(", ", names.subList(0, 3)) + " and " + (names.size() - 3) + " others";
        }
        if (names.size() == 1) return names.get(0);
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    /** What the file really is, from its first bytes: JPEG, PNG or PDF; null for anything else. */
    private static String contentType(MultipartFile file) {
        byte[] head = new byte[8];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return null;
        }
        if (read >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (read >= 4 && (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') return "image/png";
        if (read >= 4 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') return "application/pdf";
        return null;
    }

    /** The person's file name without any folders, for display only. */
    private static String displayName(String original, String extension) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) name = "evidence." + extension;
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private TrainingEvidence evidenceOf(Long sessionId, Long evidenceId) {
        return trainingEvidenceRepository.findById(evidenceId).filter(e -> e.getTrainingId().equals(sessionId))
                .orElseThrow(() -> new BadRequestException("That file isn't attached to this session."));
    }

    private void audit(TrainingViewer viewer, TrainingAuditAction action, TrainingProgram s, String reason, String detail) {
        trainingAuditService.record(viewer.userId(), action, TrainingAuditTarget.SESSION, String.valueOf(s.getTrainingId()),
                reason, detail);
    }

    private TrainingProgram find(Long id) {
        return trainingProgramRepository.findByTrainingIdAndDeletedFalse(id)
                .orElseThrow(() -> new BadRequestException("This session no longer exists."));
    }

    private TrainingProgram locked(Long id) {
        return trainingProgramRepository.lockById(id)
                .orElseThrow(() -> new BadRequestException("This session no longer exists."));
    }
}
