package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopCompetencyScoreDto;
import com.tbm.careerpathlearning.dto.SopEnrollmentProgressDto;
import com.tbm.careerpathlearning.dto.SopScoreboardDto;
import com.tbm.careerpathlearning.dto.SopScoreboardRecordDto;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizAttempt;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopEnrollmentRepository;
import com.tbm.careerpathlearning.repository.SopModuleRepository;
import com.tbm.careerpathlearning.repository.SopQuizAttemptRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.SopCourseReadiness;
import com.tbm.careerpathlearning.service.SopProgress;
import com.tbm.careerpathlearning.service.SopScoreboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SopScoreboardServiceImpl implements SopScoreboardService {

    static final String NOT_STARTED = "NOT_STARTED";
    static final String IN_PROGRESS = "IN_PROGRESS";
    static final String RETRAINING = "RETRAINING";
    static final String COMPLETED = "COMPLETED";
    static final String WITHDRAWN = "WITHDRAWN";

    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private SopModuleRepository sopModuleRepository;
    @Autowired private SopEnrollmentRepository sopEnrollmentRepository;
    @Autowired private SopQuizAttemptRepository sopQuizAttemptRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private SopCourseReadiness sopCourseReadiness;
    @Autowired private Clock clock;

    @Override
    @Transactional(readOnly = true)
    public SopScoreboardDto scoreboard(UUID userId, boolean seeAll, boolean team) {
        Map<Long, SopDocument> courses = sopDocumentRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .collect(Collectors.toMap(SopDocument::getId, Function.identity()));
        Set<UUID> reports = team && !seeAll ? teamOf(userId) : Set.of();
        boolean ownsCourses = courses.values().stream().anyMatch(d -> userId.equals(d.getUploadedBy()));
        // Every course, the courses the viewer owns, and for a manager every course their team is taking (FR-10-10).
        List<SopEnrollment> enrollments = courses.isEmpty() ? List.of() : sopEnrollmentRepository.findAllBySopDocumentIdIn(courses.keySet())
                .stream().filter(e -> seeAll || userId.equals(courses.get(e.getSopDocumentId()).getUploadedBy())
                        || reports.contains(e.getStaffId()))
                .toList();
        Map<Long, SopDocument> docs = enrollments.stream().map(e -> courses.get(e.getSopDocumentId())).distinct()
                .collect(Collectors.toMap(SopDocument::getId, Function.identity()));
        SopScoreboardDto dto = new SopScoreboardDto();
        dto.setScope(seeAll ? "ALL" : !team ? "OWN" : ownsCourses ? "OWN_AND_TEAM" : "TEAM");
        dto.setGeneratedAt(OffsetDateTime.now(clock));
        Set<Long> enrolledDocs = enrollments.stream().map(SopEnrollment::getSopDocumentId).collect(Collectors.toSet());
        // Courses someone is enrolled in, for the course filter.
        docs.values().stream().filter(d -> enrolledDocs.contains(d.getId()))
                .sorted(Comparator.comparing(SopDocument::getTitle, String.CASE_INSENSITIVE_ORDER))
                .forEach(d -> {
                    SopScoreboardDto.Course c = new SopScoreboardDto.Course();
                    c.setId(d.getId());
                    c.setTitle(d.getTitle());
                    c.setArchived(d.isArchived());
                    c.setVersionNo(d.getVersionNo());
                    c.setSuperseded(d.isSuperseded());
                    dto.getCourses().add(c);
                });
        if (enrollments.isEmpty()) {
            return dto;
        }

        // Load everything in a few queries rather than per enrolment.
        List<SopModule> allModules = sopModuleRepository.findAllBySopDocumentIdInOrderByModuleOrderAsc(enrolledDocs);
        Map<Long, List<SopModule>> modules = allModules.stream().collect(Collectors.groupingBy(SopModule::getSopDocumentId));
        Map<Long, List<SopQuizQuestion>> quizzes = sopCourseReadiness.quizzesOf(allModules);
        Map<Long, List<SopQuizAttempt>> attempts = sopQuizAttemptRepository
                .findAllByEnrollmentIdInOrderByAttemptedAtAscIdAsc(enrollments.stream().map(SopEnrollment::getId).toList())
                .stream().collect(Collectors.groupingBy(SopQuizAttempt::getEnrollmentId));
        Map<UUID, Staff> staff = staffRepository.findAllWithRoleByIdIn(
                        enrollments.stream().map(SopEnrollment::getStaffId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Staff::getId, Function.identity()));

        for (SopEnrollment e : enrollments) {
            SopProgress progress = SopProgress.of(modules.getOrDefault(e.getSopDocumentId(), List.of()), quizzes,
                    attempts.getOrDefault(e.getId(), List.of()), e.getPassMark());
            SopScoreboardRecordDto r = record(e, docs.get(e.getSopDocumentId()), progress, staff.get(e.getStaffId()));
            // The person's whole training record: for HR and the Super Admin, and for their manager (FR-18-01).
            r.setCanOpenRecord(seeAll || reports.contains(e.getStaffId()));
            dto.getRecords().add(r);
        }
        dto.getRecords().sort(Comparator.comparing(SopScoreboardRecordDto::getStaffName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SopScoreboardRecordDto::getCourseTitle, String.CASE_INSENSITIVE_ORDER));
        dto.setDepartments(dto.getRecords().stream().map(SopScoreboardRecordDto::getDepartment)
                .filter(Objects::nonNull).distinct().sorted().toList());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public SopEnrollmentProgressDto enrollment(Long enrollmentId, UUID userId, boolean seeAll, boolean team) {
        SopEnrollment e = sopEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BadRequestException("Enrolment not found."));
        SopDocument doc = sopDocumentRepository.findById(e.getSopDocumentId()).filter(d -> !d.isDeleted())
                .orElseThrow(() -> new BadRequestException("Course not found."));
        if (!seeAll && !userId.equals(doc.getUploadedBy()) && !(team && teamOf(userId).contains(e.getStaffId()))) {
            throw new ForbiddenRequestException(team ? "You can only view progress on your team's training or courses you created."
                    : "You can only view progress on courses you created.");
        }
        List<SopModule> modules = sopModuleRepository.findAllBySopDocumentIdOrderByModuleOrderAsc(doc.getId());
        SopProgress progress = SopProgress.of(modules, sopCourseReadiness.quizzesOf(modules),
                sopQuizAttemptRepository.findAllByEnrollmentIdOrderByAttemptedAtAscIdAsc(e.getId()), e.getPassMark());
        Staff staff = staffRepository.findAllWithRoleByIdIn(List.of(e.getStaffId())).stream().findFirst().orElse(null);

        SopEnrollmentProgressDto dto = new SopEnrollmentProgressDto();
        dto.setRecord(record(e, doc, progress, staff));
        for (SopProgress.Module m : progress.modules()) {
            SopEnrollmentProgressDto.Module md = new SopEnrollmentProgressDto.Module();
            md.setModuleId(m.module().getId());
            md.setModuleOrder(m.module().getModuleOrder());
            md.setTitle(m.module().getTitle());
            md.setState(m.state());
            md.setPassedScore(m.passedScore());
            for (SopQuizAttempt a : m.attempts()) {
                SopEnrollmentProgressDto.Attempt ad = new SopEnrollmentProgressDto.Attempt();
                ad.setScore(a.getScore());
                ad.setCorrect(a.getCorrect());
                ad.setTotal(a.getTotal());
                ad.setPassed(a.isPassed());
                ad.setAttemptedAt(a.getAttemptedAt());
                md.getAttempts().add(ad);
            }
            dto.getModules().add(md);
        }
        return dto;
    }

    @Override
    public List<SopCompetencyScoreDto> competencyScores(UUID staffId) {
        Staff staff = staffRepository.findById(staffId).orElseThrow(() -> new BadRequestException("Staff not found."));
        return sopEnrollmentRepository.findAllByStaffId(staffId).stream()
                .filter(e -> e.getStatus() == SopEnrollmentStatus.COMPLETED && e.getFinalScore() != null)
                .map(e -> sopDocumentRepository.findById(e.getSopDocumentId()).filter(d -> !d.isDeleted())
                        .map(doc -> competency(e, doc, staff)).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SopCompetencyScoreDto::getCompletedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /** The active staff who report directly to the manager (decision C: direct reports only). */
    private Set<UUID> teamOf(UUID managerId) {
        return staffRepository.findAllActiveWithRole().stream()
                .filter(s -> !s.isDeleted() && s.getManager() != null && managerId.equals(s.getManager().getId()))
                .map(Staff::getId).collect(Collectors.toSet());
    }

    // === mapping ==============================================================

    private SopScoreboardRecordDto record(SopEnrollment e, SopDocument doc, SopProgress progress, Staff staff) {
        SopScoreboardRecordDto r = new SopScoreboardRecordDto();
        r.setEnrollmentId(e.getId());
        r.setStaffId(e.getStaffId());
        r.setStaffName(staff == null ? "Former staff member" : staff.getName());
        r.setJobRole(staff == null || staff.getRole() == null ? null : staff.getRole().getName());
        OrgChart department = staff == null ? null : SopAssignmentServiceImpl.department(staff);
        r.setDepartment(department == null ? null : department.getName());
        r.setSopDocumentId(doc.getId());
        r.setCourseTitle(doc.getTitle());
        r.setCourseArchived(doc.isArchived());
        r.setVersionNo(doc.getVersionNo());
        r.setCourseSuperseded(doc.isSuperseded());
        r.setModuleCount(progress.modules().size());
        r.setModulesPassed(progress.passed());
        r.setStatus(status(e, progress));
        LocalDate due = e.dueDate();
        r.setDueDate(due);
        r.setOverdue(e.isMandatory() && e.isActive() && due != null && due.isBefore(LocalDate.now(clock))); // never optional (FR-11-17)
        r.setMandatory(e.isMandatory());
        r.setMode(e.getMode());
        r.setSelfEnrolled(e.getAssignmentId() == null);
        // Left the company: gone from the staff list, or no longer active (FR-10-12).
        r.setLeftCompany(staff == null || staff.isDeleted() || staff.getAccountStatus() != StaffAccountStatus.ACTIVE);
        r.setPassMark(e.getPassMark());
        r.setFinalScore(e.getFinalScore());
        r.setScore(e.getFinalScore() != null ? e.getFinalScore() : progress.averagePassedScore());
        r.setAttempts(progress.attempts());
        r.setRetrainingAttempts(progress.failedAttempts());
        r.setLastAttemptAt(progress.modules().stream().flatMap(m -> m.attempts().stream())
                .map(SopQuizAttempt::getAttemptedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null));
        r.setAssignedAt(e.getAssignedAt());
        r.setStartedAt(e.getStartedAt());
        r.setCompletedAt(e.getCompletedAt());
        return r;
    }

    /**
     * Completed, withdrawn from an archived course (FR-06-15), in retraining after a failed quiz (FR-10-04), in
     * progress, or not started.
     */
    static String status(SopEnrollment e, SopProgress progress) {
        if (e.getStatus() == SopEnrollmentStatus.COMPLETED) {
            return COMPLETED;
        }
        if (e.getStatus() == SopEnrollmentStatus.WITHDRAWN) {
            return WITHDRAWN;
        }
        if (progress.retraining()) {
            return RETRAINING;
        }
        return e.getStatus() == SopEnrollmentStatus.IN_PROGRESS ? IN_PROGRESS : NOT_STARTED;
    }

    private static SopCompetencyScoreDto competency(SopEnrollment e, SopDocument doc, Staff staff) {
        SopCompetencyScoreDto dto = new SopCompetencyScoreDto();
        dto.setStaffId(e.getStaffId());
        dto.setStaffName(staff.getName());
        dto.setSopDocumentId(doc.getId());
        dto.setCourseTitle(doc.getTitle());
        dto.setVersion(doc.getVersion());
        dto.setCategory(doc.getDepartmentTag());
        dto.setFinalScore(e.getFinalScore());
        dto.setPassMark(e.getPassMark());
        dto.setCompletedAt(e.getCompletedAt());
        dto.setCertificateNo(e.certificateNo());
        return dto;
    }
}
