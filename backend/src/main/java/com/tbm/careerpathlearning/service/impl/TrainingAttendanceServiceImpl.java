package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrainingAttendanceRequest;
import com.tbm.careerpathlearning.dto.TrainingAttendanceSheetDto;
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
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
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
        Set<UUID> people = new HashSet<>(registered(id));
        people.addAll(rows.keySet());
        List<TrainingEvidence> evidence = trainingEvidenceRepository.findAllByTrainingIdOrderByUploadedAtAscEvidenceIdAsc(id);
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
        boolean recorder = recorder(s, viewer);
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
