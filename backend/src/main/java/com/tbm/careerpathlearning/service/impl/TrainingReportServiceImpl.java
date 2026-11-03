package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.TrainingAttendanceSheetDto;
import com.tbm.careerpathlearning.dto.TrainingSessionReportDto;
import com.tbm.careerpathlearning.enums.TrainingAttendanceMethod;
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
import com.tbm.careerpathlearning.service.TrainingAuditService;
import com.tbm.careerpathlearning.service.TrainingDownload;
import com.tbm.careerpathlearning.service.TrainingReportService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TrainingReportServiceImpl implements TrainingReportService {

    private static final String[] PEOPLE_COLUMNS = {"Name", "Email", "IC or passport no.", "Job role", "Department",
            "Branch", "Walk-in", "Attendance", "Quiz score", "Recorded", "Recorded by", "Recorded at"};

    @Autowired private TrainingProgramRepository trainingProgramRepository;
    @Autowired private TrainingRegistrationRepository trainingRegistrationRepository;
    @Autowired private TrainingAttendanceRepository trainingAttendanceRepository;
    @Autowired private TrainingEvidenceRepository trainingEvidenceRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private TrainingAuditService trainingAuditService;
    /** Malaysia time, whatever time zone the server runs in. */
    @Autowired private Clock clock;

    @Override
    @Transactional(readOnly = true)
    public TrainingSessionReportDto report(Long sessionId, TrainingViewer viewer) {
        return build(viewable(sessionId, viewer), viewer);
    }

    @Override
    @Transactional
    public TrainingDownload reportFile(Long sessionId, TrainingViewer viewer) {
        TrainingProgram s = viewable(sessionId, viewer);
        TrainingSessionReportDto r = build(s, viewer);
        byte[] content = workbook(s, r);
        if (r.getPeople().stream().anyMatch(p -> p.getIdNumber() != null)) {
            trainingAuditService.record(viewer.userId(), TrainingAuditAction.IC_NUMBERS_EXPORTED, TrainingAuditTarget.SESSION,
                    String.valueOf(sessionId), null, "Session report with IC numbers");
        }
        return new TrainingDownload(content, TrainingSpreadsheets.fileName("Session report", s), TrainingSpreadsheets.XLSX);
    }

    private TrainingProgram viewable(Long sessionId, TrainingViewer viewer) {
        TrainingProgram s = trainingProgramRepository.findByTrainingIdAndDeletedFalse(sessionId)
                .orElseThrow(() -> new BadRequestException("This session no longer exists."));
        if (!TrainingAttendanceServiceImpl.recorder(s, viewer) && !viewer.superAdmin()) {
            throw new ForbiddenRequestException("Only the session's trainer, HR and the Super Admin can see its report.");
        }
        return s;
    }

    private TrainingSessionReportDto build(TrainingProgram s, TrainingViewer viewer) {
        // The HRDC details, IC or passport number in full, are for whoever records the attendance, as on the People tab.
        boolean details = TrainingAttendanceServiceImpl.recorder(s, viewer);
        Long id = s.getTrainingId();
        LocalDateTime now = LocalDateTime.now(clock);
        List<TrainingRegistration> registrations = trainingRegistrationRepository.findAllByTrainingId(id);
        Map<UUID, TrainingRegistration> registered = new HashMap<>();
        registrations.stream().filter(r -> r.getStatus() == TrainingRegistrationStatus.REGISTERED)
                .forEach(r -> registered.put(r.getStaffId(), r));
        Map<UUID, TrainingAttendance> marks = new HashMap<>();
        trainingAttendanceRepository.findAllByTrainingId(id).forEach(a -> marks.put(a.getStaffId(), a));
        List<TrainingEvidence> evidence = trainingEvidenceRepository.findAllByTrainingIdOrderByUploadedAtAscEvidenceIdAsc(id);

        Set<UUID> people = new HashSet<>(registered.keySet());
        people.addAll(marks.keySet());
        Set<UUID> lookup = new HashSet<>(people);
        marks.values().stream().map(TrainingAttendance::getRecordedBy).filter(Objects::nonNull).forEach(lookup::add);
        evidence.forEach(e -> lookup.add(e.getUploadedBy()));
        if (s.getTrainerId() != null) lookup.add(s.getTrainerId());
        if (s.getAttendanceSubmittedBy() != null) lookup.add(s.getAttendanceSubmittedBy());
        lookup.add(viewer.userId());
        Map<UUID, Staff> staff = staffRepository.findAllWithRoleByIdIn(lookup).stream()
                .collect(Collectors.toMap(Staff::getId, x -> x));

        TrainingSessionReportDto r = new TrainingSessionReportDto();
        r.setSessionId(id);
        r.setTitle(s.getTitle());
        r.setKind(s.getKind());
        r.setDelivery(s.getDelivery());
        r.setTrainingType(s.getTrainingType());
        r.setDate(s.getSessionDate());
        r.setStartTime(s.getStartTime());
        r.setEndTime(s.getEndTime());
        r.setDurationMinutes(s.durationMinutes());
        r.setVenue(s.getVenue());
        r.setExternalTrainer(s.getTrainerId() == null);
        r.setTrainerName(s.getTrainerId() == null ? s.getTrainerName() : name(staff, s.getTrainerId()));
        r.setMandatory(s.isMandatory());
        r.setStatus(s.getStatus());
        r.setTiming(s.timing(now));
        r.setCancelReason(s.getCancelReason());
        r.setCapacity(s.getCapacity());
        r.setRegistered(registered.size());
        r.setWaitlisted((int) registrations.stream().filter(x -> x.getStatus() == TrainingRegistrationStatus.WAITLISTED).count());

        for (UUID staffId : people) {
            Staff person = staff.get(staffId);
            TrainingAttendance mark = marks.get(staffId);
            TrainingRegistration form = registered.get(staffId);
            TrainingSessionReportDto.Person p = new TrainingSessionReportDto.Person();
            p.setName(person == null ? "Unknown user" : person.getName());
            if (person != null) {
                OrgChart department = SopAssignmentServiceImpl.department(person);
                p.setEmail(person.getEmail());
                p.setRoleName(person.getRole() == null ? null : person.getRole().getName());
                p.setDepartmentName(department == null ? null : department.getName());
            }
            if (form != null && details) {
                p.setBranch(form.getBranch());
                p.setIdNumber(form.getIdNumber());
            }
            p.setStatus(mark == null ? "NOT_RECORDED" : mark.getStatus().name());
            if (mark != null) {
                p.setWalkIn(mark.isWalkIn());
                p.setQuizScore(mark.getQuizScore());
                p.setMethod(mark.getMethod().name());
                p.setRecordedByName(mark.getRecordedBy() == null ? null : name(staff, mark.getRecordedBy()));
                p.setRecordedAt(mark.getRecordedAt());
            }
            r.getPeople().add(p);
        }
        r.getPeople().sort(Comparator.comparing(p -> Objects.toString(p.getName(), ""), String.CASE_INSENSITIVE_ORDER));
        r.setAttended((int) r.getPeople().stream().filter(p -> "ATTENDED".equals(p.getStatus())).count());
        r.setAbsent((int) r.getPeople().stream().filter(p -> "ABSENT".equals(p.getStatus())).count());
        r.setNotRecorded(r.getPeople().size() - r.getAttended() - r.getAbsent());
        r.setWalkIns((int) r.getPeople().stream().filter(TrainingSessionReportDto.Person::isWalkIn).count());
        r.setAttendanceRate(people.isEmpty() ? null : Math.round(r.getAttended() * 100f / people.size()));
        r.setTrainingHours(Math.round(r.getAttended() * s.durationMinutes() / 60.0 * 100) / 100.0);
        List<Integer> scores = r.getPeople().stream().map(TrainingSessionReportDto.Person::getQuizScore)
                .filter(Objects::nonNull).toList();
        r.setAverageQuizScore(scores.isEmpty() ? null
                : (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0)));

        r.setSubmitted(s.getAttendanceSubmittedAt() != null);
        r.setSubmittedAt(s.getAttendanceSubmittedAt());
        r.setSubmittedByName(s.getAttendanceSubmittedBy() == null ? null : name(staff, s.getAttendanceSubmittedBy()));
        for (TrainingEvidence e : evidence) {
            TrainingAttendanceSheetDto.Evidence ev = new TrainingAttendanceSheetDto.Evidence();
            ev.setId(e.getEvidenceId());
            ev.setFileName(e.getFileName());
            ev.setContentType(e.getContentType());
            ev.setSizeBytes(e.getSizeBytes());
            ev.setUploadedByName(name(staff, e.getUploadedBy()));
            ev.setUploadedAt(e.getUploadedAt());
            r.getEvidence().add(ev);
        }
        r.setGeneratedAt(now);
        r.setGeneratedByName(name(staff, viewer.userId()));
        return r;
    }

    /** One sheet: the session, its numbers, then the people and the evidence, all as text Excel won't change. */
    private byte[] workbook(TrainingProgram s, TrainingSessionReportDto r) {
        try (TrainingSpreadsheets.Book book = new TrainingSpreadsheets.Book()) {
            Sheet sheet = book.sheet("Report", 28, 32, 20, 22, 22, 26, 9, 14, 11, 12, 24, 18);
            int i = 0;
            book.row(sheet, i++, book.title, "Session report");
            book.row(sheet, i++, book.label, r.getTitle());
            i++;
            String trainer = Objects.toString(r.getTrainerName(), "Not set") + (r.isExternalTrainer() ? " (external)" : "");
            String status = TrainingSpreadsheets.status(s, r.getGeneratedAt());
            if (r.getStatus() == TrainingSessionStatus.CANCELLED && r.getCancelReason() != null) {
                status += ": " + r.getCancelReason();
            }
            i = pair(book, sheet, i, "Date and time", TrainingSpreadsheets.when(s) + " (" + hours(r.getDurationMinutes()) + ")");
            i = pair(book, sheet, i, "Venue", r.getVenue() == null ? "Online" : r.getVenue());
            i = pair(book, sheet, i, "Trainer", trainer);
            i = pair(book, sheet, i, "Kind", TrainingSpreadsheets.kind(r.getKind()));
            i = pair(book, sheet, i, "Delivery", TrainingSpreadsheets.delivery(r.getDelivery()));
            i = pair(book, sheet, i, "Training type", TrainingSpreadsheets.type(r.getTrainingType()));
            i = pair(book, sheet, i, "Mandatory", r.isMandatory() ? "Yes" : "No");
            i = pair(book, sheet, i, "Status", status);
            i = pair(book, sheet, i, "Attendance", r.isSubmitted()
                    ? "Submitted by " + Objects.toString(r.getSubmittedByName(), "Unknown user") + " on "
                    + r.getSubmittedAt().format(TrainingSpreadsheets.STAMP)
                    : "Not submitted yet");
            i++;
            book.row(sheet, i++, book.label, "Numbers");
            i = pair(book, sheet, i, "Seats", r.getCapacity());
            i = pair(book, sheet, i, "Registered", r.getRegistered());
            i = pair(book, sheet, i, "Waitlisted", r.getWaitlisted());
            i = pair(book, sheet, i, "Walk-ins", r.getWalkIns());
            i = pair(book, sheet, i, "Attended", r.getAttended());
            i = pair(book, sheet, i, "Absent", r.getAbsent());
            i = pair(book, sheet, i, "Not recorded", r.getNotRecorded());
            i = pair(book, sheet, i, "Attendance rate", r.getAttendanceRate() == null ? "-" : r.getAttendanceRate() + "%");
            i = pair(book, sheet, i, "Training hours (attended x length)", r.getTrainingHours());
            i = pair(book, sheet, i, "Average quiz score", r.getAverageQuizScore() == null ? "-" : r.getAverageQuizScore() + "%");
            i++;
            book.row(sheet, i++, book.label, "People");
            book.row(sheet, i++, book.header, (Object[]) PEOPLE_COLUMNS);
            for (TrainingSessionReportDto.Person p : r.getPeople()) {
                book.row(sheet, i++, null, p.getName(), p.getEmail(), p.getIdNumber(), p.getRoleName(), p.getDepartmentName(),
                        p.getBranch(), p.isWalkIn() ? "Yes" : "No", attendance(p.getStatus()), p.getQuizScore(),
                        p.getMethod() == null ? null
                                : TrainingAttendanceMethod.BATCH.name().equals(p.getMethod()) ? "File upload" : "By hand",
                        p.getRecordedByName(),
                        p.getRecordedAt() == null ? null : p.getRecordedAt().format(TrainingSpreadsheets.STAMP));
            }
            if (r.getPeople().isEmpty()) {
                book.row(sheet, i++, book.note, "Nobody registered or attended.");
            }
            i++;
            book.row(sheet, i++, book.label, "Evidence");
            for (TrainingAttendanceSheetDto.Evidence e : r.getEvidence()) {
                book.row(sheet, i++, null, e.getFileName(), "Attached by " + Objects.toString(e.getUploadedByName(), "Unknown user")
                        + ", " + e.getUploadedAt().format(TrainingSpreadsheets.STAMP));
            }
            if (r.getEvidence().isEmpty()) {
                book.row(sheet, i++, book.note, "None attached.");
            }
            i++;
            book.row(sheet, i, book.note, "Made by " + r.getGeneratedByName() + " on "
                    + r.getGeneratedAt().format(TrainingSpreadsheets.STAMP) + ", Malaysia time.");
            return book.bytes();
        }
    }

    private static int pair(TrainingSpreadsheets.Book book, Sheet sheet, int i, String label, Object value) {
        book.row(sheet, i, null, label, value).getCell(0).setCellStyle(book.label);
        return i + 1;
    }

    private static String attendance(String status) {
        return switch (status) {
            case "ATTENDED" -> "Attended";
            case "ABSENT" -> "Absent";
            default -> "Not recorded";
        };
    }

    /** "3 h", "1 h 30 min", "45 min" */
    private static String hours(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return h == 0 ? m + " min" : m == 0 ? h + " h" : h + " h " + m + " min";
    }

    private static String name(Map<UUID, Staff> staff, UUID id) {
        Staff s = staff.get(id);
        return s == null ? "Unknown user" : s.getName();
    }
}
