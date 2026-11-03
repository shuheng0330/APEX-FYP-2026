package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.config.ClockConfig;
import com.tbm.careerpathlearning.dto.TrainingRecordDto;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.enums.TrainingAttendanceStatus;
import com.tbm.careerpathlearning.enums.TrainingSessionStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizAttempt;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.model.TrainingAttendance;
import com.tbm.careerpathlearning.model.TrainingProgram;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopEnrollmentRepository;
import com.tbm.careerpathlearning.repository.SopModuleRepository;
import com.tbm.careerpathlearning.repository.SopQuizAttemptRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.repository.TrainingAttendanceRepository;
import com.tbm.careerpathlearning.repository.TrainingProgramRepository;
import com.tbm.careerpathlearning.service.SopCourseReadiness;
import com.tbm.careerpathlearning.service.SopProgress;
import com.tbm.careerpathlearning.service.TrainingRecordService;
import com.tbm.careerpathlearning.service.TrainingViewer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TrainingRecordServiceImpl implements TrainingRecordService {

    @Autowired private StaffRepository staffRepository;
    @Autowired private SopEnrollmentRepository sopEnrollmentRepository;
    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private SopModuleRepository sopModuleRepository;
    @Autowired private SopQuizAttemptRepository sopQuizAttemptRepository;
    @Autowired private SopCourseReadiness sopCourseReadiness;
    @Autowired private TrainingAttendanceRepository trainingAttendanceRepository;
    @Autowired private TrainingProgramRepository trainingProgramRepository;
    /** Malaysia time, whatever time zone the server runs in. */
    @Autowired private Clock clock;

    @Override
    @Transactional(readOnly = true)
    public TrainingRecordDto record(UUID staffId, TrainingViewer viewer) {
        Staff person = staffRepository.findAllWithRoleByIdIn(List.of(staffId)).stream().findFirst()
                .orElseThrow(() -> new BadRequestException("This staff member couldn't be found."));
        boolean own = viewer.userId().equals(staffId);
        boolean manager = person.getManager() != null && viewer.userId().equals(person.getManager().getId());
        if (!own && !manager && !viewer.hr() && !viewer.superAdmin()) {
            throw new ForbiddenRequestException("You can see your own training record, and your direct reports' if you "
                    + "manage anyone.");
        }
        LocalDate today = LocalDate.now(clock);
        TrainingRecordDto dto = new TrainingRecordDto();
        dto.setStaffId(staffId);
        dto.setName(person.getName());
        dto.setEmail(person.getEmail());
        OrgChart department = SopAssignmentServiceImpl.department(person);
        dto.setRoleName(person.getRole() == null ? null : person.getRole().getName());
        dto.setDepartmentName(department == null ? null : department.getName());
        dto.setLeftCompany(person.isDeleted() || person.getAccountStatus() != StaffAccountStatus.ACTIVE);

        courses(staffId, today, dto);
        sessions(staffId, dto);

        // Newest first: what was finished, or is due, most recently.
        dto.getItems().sort(Comparator.comparing((TrainingRecordDto.Item i) ->
                i.getCompletedOn() != null ? i.getCompletedOn() : i.getHeldOn() != null ? i.getHeldOn()
                        : i.getDueDate() != null ? i.getDueDate() : LocalDate.MIN)
                .reversed().thenComparing(TrainingRecordDto.Item::getTitle, String.CASE_INSENSITIVE_ORDER));
        dto.getCertificates().sort(Comparator.comparing(TrainingRecordDto.Certificate::getIssuedOn).reversed());

        // FR-18-03
        List<TrainingRecordDto.Item> done = dto.getItems().stream()
                .filter(i -> "COMPLETED".equals(i.getStatus()) || "ATTENDED".equals(i.getStatus())).toList();
        dto.setCompletedItems(done.size());
        dto.setCourseHours(hours(done, "COURSE"));
        dto.setSessionHours(hours(done, "SESSION"));
        dto.setTotalHours(round(dto.getCourseHours() + dto.getSessionHours()));
        dto.setLastCompleted(done.stream().map(TrainingRecordDto.Item::getCompletedOn).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null));
        dto.setMandatoryOutstanding((int) dto.getItems().stream().filter(i -> "COURSE".equals(i.getType()) && i.isMandatory()
                && Set.of("NOT_STARTED", "IN_PROGRESS", "OVERDUE").contains(i.getStatus())).count());
        return dto;
    }

    /** Every course the person was ever enrolled in, any version, with each module's quiz result. */
    private void courses(UUID staffId, LocalDate today, TrainingRecordDto dto) {
        List<SopEnrollment> enrollments = sopEnrollmentRepository.findAllByStaffId(staffId);
        if (enrollments.isEmpty()) {
            return;
        }
        Map<Long, SopDocument> docs = sopDocumentRepository.findAllById(enrollments.stream()
                .map(SopEnrollment::getSopDocumentId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SopDocument::getId, Function.identity()));
        Map<Long, List<SopModule>> modules = sopModuleRepository.findAllBySopDocumentIdInOrderByModuleOrderAsc(docs.keySet())
                .stream().collect(Collectors.groupingBy(SopModule::getSopDocumentId));
        Map<Long, List<SopQuizAttempt>> attempts = sopQuizAttemptRepository.findAllByEnrollmentIdInOrderByAttemptedAtAscIdAsc(
                enrollments.stream().map(SopEnrollment::getId).toList()).stream()
                .collect(Collectors.groupingBy(SopQuizAttempt::getEnrollmentId));
        for (SopEnrollment e : enrollments) {
            SopDocument doc = docs.get(e.getSopDocumentId());
            if (doc == null) continue;
            List<SopModule> courseModules = modules.getOrDefault(doc.getId(), List.of());
            SopProgress progress = SopProgress.of(courseModules, sopCourseReadiness.quizzesOf(courseModules),
                    attempts.getOrDefault(e.getId(), List.of()), e.getPassMark());
            LocalDate due = e.dueDate();
            boolean overdue = e.isMandatory() && e.isActive() && due != null && due.isBefore(today); // FR-18-02
            boolean completed = e.getStatus() == SopEnrollmentStatus.COMPLETED;

            TrainingRecordDto.Item item = new TrainingRecordDto.Item();
            item.setType("COURSE");
            item.setId(doc.getId());
            item.setTitle(doc.getTitle());
            item.setVersionNo(doc.getVersionNo());
            item.setStatus(overdue ? "OVERDUE" : e.getStatus().name());
            item.setMandatory(e.isMandatory());
            item.setDelivery("E-learning");
            item.setDueDate(due);
            item.setCompletedOn(completed && e.getCompletedAt() != null
                    ? e.getCompletedAt().atZoneSameInstant(ClockConfig.ZONE).toLocalDate() : null);
            item.setScore(e.getFinalScore());
            if (completed) {
                // The length counted is the one set when the person was enrolled (FR-07-15).
                int minutes = e.getDurationMinutes() != null ? e.getDurationMinutes() : doc.effectiveDurationMinutes();
                item.setHours(round(minutes / 60.0));
            }
            for (SopProgress.Module m : progress.modules()) {
                TrainingRecordDto.ModuleScore ms = new TrainingRecordDto.ModuleScore();
                ms.setTitle(m.module().getTitle());
                ms.setPassed(SopProgress.PASSED.equals(m.state()));
                ms.setScore(ms.isPassed() ? m.passedScore() : m.bestScore());
                ms.setAttempts(m.attempts().size());
                item.getModules().add(ms);
            }
            dto.getItems().add(item);

            if (completed) { // FR-18-05
                TrainingRecordDto.Certificate c = new TrainingRecordDto.Certificate();
                c.setCertificateNo(e.certificateNo());
                c.setCourse(doc.getTitle());
                c.setVersionNo(doc.getVersionNo());
                c.setIssuedOn(item.getCompletedOn());
                c.setFinalScore(e.getFinalScore());
                dto.getCertificates().add(c);
            }
        }
    }

    /** Sessions whose attendance was submitted: only then is it the record (FR-14-09). */
    private void sessions(UUID staffId, TrainingRecordDto dto) {
        List<TrainingAttendance> marks = trainingAttendanceRepository.findAllByStaffId(staffId);
        if (marks.isEmpty()) {
            return;
        }
        Map<Long, TrainingProgram> sessions = trainingProgramRepository.findAllById(marks.stream()
                .map(TrainingAttendance::getTrainingId).collect(Collectors.toSet())).stream()
                .filter(s -> !s.isDeleted() && s.getStatus() != TrainingSessionStatus.CANCELLED
                        && s.getAttendanceSubmittedAt() != null)
                .collect(Collectors.toMap(TrainingProgram::getTrainingId, Function.identity()));
        for (TrainingAttendance a : marks) {
            TrainingProgram s = sessions.get(a.getTrainingId());
            if (s == null) continue;
            boolean attended = a.getStatus() == TrainingAttendanceStatus.ATTENDED;
            TrainingRecordDto.Item item = new TrainingRecordDto.Item();
            item.setType("SESSION");
            item.setId(s.getTrainingId());
            item.setTitle(s.getTitle());
            item.setStatus(a.getStatus().name());
            item.setMandatory(s.isMandatory());
            item.setDelivery(TrainingSpreadsheets.delivery(s.getDelivery()));
            item.setKind(TrainingSpreadsheets.kind(s.getKind()));
            item.setTrainingType(s.getTrainingType() == null ? null : TrainingSpreadsheets.type(s.getTrainingType()));
            item.setHeldOn(s.getSessionDate());
            item.setCompletedOn(attended ? s.getSessionDate() : null);
            item.setScore(a.getQuizScore());
            if (attended) {
                item.setHours(round(s.durationMinutes() / 60.0)); // the scheduled length (FR-17-04)
            }
            dto.getItems().add(item);
        }
    }

    private static double hours(List<TrainingRecordDto.Item> done, String type) {
        return round(done.stream().filter(i -> type.equals(i.getType()) && i.getHours() != null)
                .mapToDouble(TrainingRecordDto.Item::getHours).sum());
    }

    private static double round(double hours) {
        return Math.round(hours * 100) / 100.0;
    }
}
