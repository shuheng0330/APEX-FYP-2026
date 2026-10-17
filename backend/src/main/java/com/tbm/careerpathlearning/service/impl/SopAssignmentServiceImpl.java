package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopAssignableCourseDto;
import com.tbm.careerpathlearning.dto.SopAssignmentDto;
import com.tbm.careerpathlearning.dto.SopAssignmentPreviewDto;
import com.tbm.careerpathlearning.dto.SopAssignmentRequest;
import com.tbm.careerpathlearning.dto.SopAssignmentResultDto;
import com.tbm.careerpathlearning.dto.SopAudienceDto;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.OrgChartType;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.enums.SopVisibility;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.SopAssignment;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.repository.SopAssignmentRepository;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopEnrollmentRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.SopAssignmentService;
import com.tbm.careerpathlearning.service.SopCourseReadiness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SopAssignmentServiceImpl implements SopAssignmentService {

    private static final int MAX_DAYS_TO_COMPLETE = 365;

    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private SopCourseReadiness sopCourseReadiness;
    @Autowired private SopAssignmentRepository sopAssignmentRepository;
    @Autowired private SopEnrollmentRepository sopEnrollmentRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private NotificationService notificationService;

    private static final DateTimeFormatter NOTICE_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    @Autowired private Clock clock;

    @Override
    public List<SopAssignableCourseDto> listCourses(UUID userId, boolean canAssignAny) {
        List<SopDocument> docs = assignableDocs(userId, canAssignAny);
        if (docs.isEmpty()) {
            return List.of();
        }
        List<Staff> staff = staffRepository.findAllActiveWithRole();
        Map<Long, Long> enrolled = sopEnrollmentRepository
                .findAllBySopDocumentIdIn(docs.stream().map(SopDocument::getId).toList()).stream()
                .collect(Collectors.groupingBy(SopEnrollment::getSopDocumentId, Collectors.counting()));
        Map<UUID, String> names = new HashMap<>();
        return docs.stream()
                .map(d -> toCourseDto(d, userId, staff, enrolled.getOrDefault(d.getId(), 0L).intValue(), names))
                .toList();
    }

    @Override
    public SopAudienceDto getAudience() {
        Map<Long, SopAudienceDto.Group> departments = new LinkedHashMap<>();
        Map<Long, SopAudienceDto.Group> roles = new LinkedHashMap<>();
        SopAudienceDto dto = new SopAudienceDto();
        for (Staff s : byName(staffRepository.findAllActiveWithRole())) {
            OrgChart dept = department(s);
            Role role = s.getRole();
            if (dept != null) {
                SopAudienceDto.Group g = departments.computeIfAbsent(dept.getId(), id -> group(id, dept.getName(), null));
                g.setStaffCount(g.getStaffCount() + 1);
            }
            if (role != null && role.isVisible() && !role.isDeleted()) {
                SopAudienceDto.Group g = roles.computeIfAbsent(role.getId(),
                        id -> group(id, role.getName(), dept == null ? null : dept.getName()));
                g.setStaffCount(g.getStaffCount() + 1);
            }
            SopAudienceDto.Person p = new SopAudienceDto.Person();
            p.setId(s.getId());
            p.setName(s.getName());
            p.setEmail(s.getEmail());
            p.setRoleId(role == null ? null : role.getId());
            p.setRoleName(role == null ? null : role.getName());
            p.setDepartmentId(dept == null ? null : dept.getId());
            p.setDepartmentName(dept == null ? null : dept.getName());
            dto.getStaff().add(p);
        }
        dto.setDepartments(departments.values().stream().sorted(Comparator.comparing(SopAudienceDto.Group::getName)).toList());
        dto.setRoles(roles.values().stream().sorted(Comparator.comparing(SopAudienceDto.Group::getName)).toList());
        return dto;
    }

    @Override
    public SopAssignmentPreviewDto preview(SopAssignmentRequest request) {
        SopDocument doc = getDocOrThrow(request.getSopDocumentId());
        Map<UUID, SopEnrollment> enrolled = enrollmentsByStaff(doc.getId());
        SopAssignmentPreviewDto dto = new SopAssignmentPreviewDto();
        for (Staff s : recipients(request, staffRepository.findAllActiveWithRole())) {
            SopEnrollment e = enrolled.get(s.getId());
            String outcome = e == null ? "NEW" : e.getStatus() == SopEnrollmentStatus.COMPLETED ? "COMPLETED" : "UPDATE";
            switch (outcome) {
                case "NEW" -> dto.setNewCount(dto.getNewCount() + 1);
                case "UPDATE" -> dto.setUpdateCount(dto.getUpdateCount() + 1);
                default -> dto.setCompletedCount(dto.getCompletedCount() + 1);
            }
            OrgChart dept = department(s);
            SopAssignmentPreviewDto.Row row = new SopAssignmentPreviewDto.Row();
            row.setStaffId(s.getId());
            row.setName(s.getName());
            row.setRoleName(s.getRole() == null ? null : s.getRole().getName());
            row.setDepartmentName(dept == null ? null : dept.getName());
            row.setOutcome(outcome);
            dto.getStaff().add(row);
        }
        return dto;
    }

    @Override
    @Transactional
    public SopAssignmentResultDto assign(SopAssignmentRequest request, UUID userId, boolean canAssignAny) {
        SopDocument doc = getDocOrThrow(request.getSopDocumentId());
        checkCanAssign(doc, userId, canAssignAny);
        if (!sopCourseReadiness.of(doc).ready()) {
            throw new BadRequestException("Approve every module and quiz of this course before assigning it.");
        }
        Terms terms = validTerms(request, doc);
        List<Staff> active = staffRepository.findAllActiveWithRole();
        List<Staff> recipients = recipients(request, active);
        if (recipients.isEmpty()) {
            throw new BadRequestException("Nobody matches the selected audience.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        SopAssignment assignment = new SopAssignment();
        assignment.setSopDocumentId(doc.getId());
        assignment.setAssignedBy(userId);
        assignment.setAudience(audienceLabel(request, active));
        assignment.setMode(terms.mode());
        assignment.setMandatory(request.isMandatory());
        assignment.setEffectiveDate(terms.effectiveDate());
        assignment.setDeadline(terms.deadline());
        assignment.setDaysToComplete(terms.daysToComplete());
        assignment.setPassMark(terms.passMark());
        assignment.setCreatedAt(now);
        assignment = sopAssignmentRepository.save(assignment);

        // One enrollment per person: new staff are enrolled, enrolled staff get the new terms,
        // and anyone who already completed the course is left as they are.
        Map<UUID, SopEnrollment> enrolled = enrollmentsByStaff(doc.getId());
        List<SopEnrollment> changed = new ArrayList<>();
        Set<UUID> newcomers = new HashSet<>();
        int assigned = 0;
        int updated = 0;
        int skipped = 0;
        for (Staff s : recipients) {
            SopEnrollment e = enrolled.get(s.getId());
            if (e != null && e.getStatus() == SopEnrollmentStatus.COMPLETED) {
                skipped++;
                continue;
            }
            if (e == null) {
                e = new SopEnrollment();
                e.setSopDocumentId(doc.getId());
                e.setStaffId(s.getId());
                e.setStatus(SopEnrollmentStatus.NOT_STARTED);
                e.setAssignedAt(now);
                newcomers.add(s.getId());
                assigned++;
            } else {
                updated++;
            }
            e.setAssignmentId(assignment.getId());
            e.setMode(terms.mode());
            e.setMandatory(request.isMandatory());
            e.setEffectiveDate(terms.effectiveDate());
            e.setDeadline(terms.deadline());
            e.setDaysToComplete(terms.daysToComplete());
            e.setPassMark(terms.passMark());
            e.setDurationMinutes(doc.effectiveDurationMinutes());
            e.setUpdatedAt(now);
            changed.add(e);
        }
        if (changed.isEmpty()) {
            throw new BadRequestException("Everyone in this audience has already completed the course.");
        }
        sopEnrollmentRepository.saveAll(changed);
        assignment.setStaffCount(changed.size());
        assignment = sopAssignmentRepository.save(assignment);
        notifyAssigned(doc, changed, newcomers, nameOf(userId, active));

        SopAssignmentResultDto result = new SopAssignmentResultDto();
        result.setAssignment(toAssignmentDto(assignment, doc.getTitle(), new HashMap<>()));
        result.setAssigned(assigned);
        result.setUpdated(updated);
        result.setSkippedCompleted(skipped);
        return result;
    }

    @Override
    public List<SopAssignmentDto> history(Long sopDocumentId, UUID userId, boolean canAssignAny) {
        Map<Long, String> titles = assignableDocs(userId, canAssignAny).stream()
                .filter(d -> sopDocumentId == null || d.getId().equals(sopDocumentId))
                .collect(Collectors.toMap(SopDocument::getId, SopDocument::getTitle));
        if (titles.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = new HashMap<>();
        return sopAssignmentRepository.findAllBySopDocumentIdInOrderByCreatedAtDesc(titles.keySet()).stream()
                .map(a -> toAssignmentDto(a, titles.get(a.getSopDocumentId()), names))
                .toList();
    }

    @Override
    @Transactional
    public SopAssignableCourseDto setVisibility(Long sopDocumentId, SopVisibility visibility, UUID userId,
                                                boolean canAssignAny) {
        if (visibility == null) {
            throw new BadRequestException("Choose Private or Open.");
        }
        SopDocument doc = getDocOrThrow(sopDocumentId);
        checkCanAssign(doc, userId, canAssignAny);
        doc.setVisibility(visibility);
        doc.setUpdatedAt(OffsetDateTime.now(clock));
        sopDocumentRepository.save(doc);
        int enrolled = sopEnrollmentRepository.findAllBySopDocumentId(sopDocumentId).size();
        return toCourseDto(doc, userId, staffRepository.findAllActiveWithRole(), enrolled, new HashMap<>());
    }

    // === helpers ============================================================

    /**
     * Tells each person their course is waiting for them (FR-09-05), or that its terms changed. Sent as part of
     * the assignment, so nothing goes out if it fails.
     */
    private void notifyAssigned(SopDocument doc, List<SopEnrollment> changed, Set<UUID> newcomers, String assignerName) {
        String link = "/my-learning/course/" + doc.getId();
        for (SopEnrollment e : changed) {
            if (newcomers.contains(e.getStaffId())) {
                notificationService.notify(e.getStaffId(), NotificationType.COURSE_ASSIGNED,
                        (e.isMandatory() ? "New mandatory training: " : "New training: ") + doc.getTitle(),
                        assignerName + " assigned you \"" + doc.getTitle() + "\". " + termsText(e), link);
            } else {
                notificationService.notify(e.getStaffId(), NotificationType.COURSE_ASSIGNMENT_UPDATED,
                        "Training updated: " + doc.getTitle(),
                        "The terms of \"" + doc.getTitle() + "\" have changed. " + termsText(e), link);
            }
        }
    }

    /** When the course opens and when it is due, in the learner's words. */
    private String termsText(SopEnrollment e) {
        boolean later = e.getEffectiveDate() != null && e.getEffectiveDate().isAfter(LocalDate.now(clock));
        if (e.getMode() == SopAssignmentMode.IMMEDIATE) {
            return (later ? "It starts on " + NOTICE_DATE.format(e.getEffectiveDate()) + ". " : "")
                    + "Complete it by " + NOTICE_DATE.format(e.dueDate()) + ".";
        }
        return (later ? "It opens in your Learning Portal on " + NOTICE_DATE.format(e.getEffectiveDate()) + ". "
                : "It is in your Learning Portal now. ")
                + "Once you start, you have " + e.getDaysToComplete() + " days to finish, and it must be finished by "
                + NOTICE_DATE.format(e.getDeadline()) + ".";
    }

    private String nameOf(UUID staffId, List<Staff> active) {
        return active.stream().filter(s -> s.getId().equals(staffId)).map(Staff::getName).findFirst()
                .or(() -> staffRepository.findById(staffId).map(Staff::getName))
                .orElse("Your trainer");
    }

    /** Generated courses the user may assign: their own, or every course with CAN_ASSIGN_TRAINING. */
    private List<SopDocument> assignableDocs(UUID userId, boolean canAssignAny) {
        return sopDocumentRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc().stream()
                .filter(d -> d.getGenerationStatus() == SopGenerationStatus.COMPLETED)
                .filter(d -> canAssignAny || userId.equals(d.getUploadedBy()))
                .toList();
    }

    private void checkCanAssign(SopDocument doc, UUID userId, boolean canAssignAny) {
        if (!canAssignAny && !userId.equals(doc.getUploadedBy())) {
            throw new ForbiddenRequestException("You can only assign courses you created."); // FR-09-07
        }
    }

    private SopDocument getDocOrThrow(Long id) {
        SopDocument doc = id == null ? null : sopDocumentRepository.findById(id).orElse(null);
        if (doc == null || doc.isDeleted()) {
            throw new BadRequestException("Select a course to assign.");
        }
        return doc;
    }

    private record Terms(SopAssignmentMode mode, LocalDate effectiveDate, LocalDate deadline, int daysToComplete,
                         int passMark) {
    }

    /** The assignment's terms; the pass mark defaults to the course's own and cannot go below it (FR-09-03). */
    private Terms validTerms(SopAssignmentRequest r, SopDocument doc) {
        if (r.getMode() == null) {
            throw new BadRequestException("Choose whether staff start immediately or from their Learning Portal.");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate effective = r.getEffectiveDate() != null ? r.getEffectiveDate() : today;
        if (effective.isBefore(today)) {
            throw new BadRequestException("The effective date cannot be in the past.");
        }
        if (r.getDeadline() == null) {
            throw new BadRequestException("Set a deadline.");
        }
        if (r.getDeadline().isBefore(effective)) {
            throw new BadRequestException("The deadline must be on or after the effective date.");
        }
        Integer days = r.getDaysToComplete();
        if (days == null || days < 1 || days > MAX_DAYS_TO_COMPLETE) {
            throw new BadRequestException("Days to complete must be between 1 and " + MAX_DAYS_TO_COMPLETE + ".");
        }
        int passMark = r.getPassMark() != null ? r.getPassMark() : doc.getPassMark();
        if (passMark < doc.getPassMark()) {
            throw new BadRequestException("The pass mark can't be lower than the course's own (" + doc.getPassMark() + "%).");
        }
        if (passMark > 100) {
            throw new BadRequestException("The pass mark must be between " + doc.getPassMark() + " and 100.");
        }
        return new Terms(r.getMode(), effective, r.getDeadline(), days, passMark);
    }

    /** Active staff in any selected department or job role, or picked by name; everyone if chosen. */
    private List<Staff> recipients(SopAssignmentRequest r, List<Staff> active) {
        if (r.isEveryone()) {
            return byName(active);
        }
        Set<Long> departments = new HashSet<>(Objects.requireNonNullElse(r.getDepartmentIds(), List.of()));
        Set<Long> roles = new HashSet<>(Objects.requireNonNullElse(r.getRoleIds(), List.of()));
        Set<UUID> people = new HashSet<>(Objects.requireNonNullElse(r.getStaffIds(), List.of()));
        return byName(active.stream().filter(s -> {
            OrgChart dept = department(s);
            return people.contains(s.getId())
                    || (s.getRole() != null && roles.contains(s.getRole().getId()))
                    || (dept != null && departments.contains(dept.getId()));
        }).toList());
    }

    /** "Everyone", or the chosen departments, job roles and people, as kept in the history. */
    private String audienceLabel(SopAssignmentRequest r, List<Staff> active) {
        if (r.isEveryone()) {
            return "Everyone";
        }
        Map<Long, String> deptNames = new HashMap<>();
        Map<Long, String> roleNames = new HashMap<>();
        Map<UUID, String> staffNames = new HashMap<>();
        for (Staff s : active) {
            OrgChart dept = department(s);
            if (dept != null) {
                deptNames.put(dept.getId(), dept.getName());
            }
            if (s.getRole() != null) {
                roleNames.put(s.getRole().getId(), s.getRole().getName() + (dept == null ? "" : " (" + dept.getName() + ")"));
            }
            staffNames.put(s.getId(), s.getName());
        }
        List<String> parts = new ArrayList<>();
        Objects.requireNonNullElse(r.getDepartmentIds(), List.<Long>of()).stream()
                .map(deptNames::get).filter(Objects::nonNull).forEach(parts::add);
        Objects.requireNonNullElse(r.getRoleIds(), List.<Long>of()).stream()
                .map(roleNames::get).filter(Objects::nonNull).forEach(parts::add);
        List<String> people = Objects.requireNonNullElse(r.getStaffIds(), List.<UUID>of()).stream()
                .map(staffNames::get).filter(Objects::nonNull).toList();
        if (people.size() > 3) {
            parts.add(String.join(", ", people.subList(0, 3)) + " and " + (people.size() - 3) + " more");
        } else {
            parts.addAll(people);
        }
        return parts.isEmpty() ? "Selected staff" : String.join("; ", parts);
    }

    private SopAssignableCourseDto toCourseDto(SopDocument d, UUID userId, List<Staff> staff, int enrolledCount,
                                               Map<UUID, String> names) {
        SopCourseReadiness.Readiness readiness = sopCourseReadiness.of(d);
        SopAssignableCourseDto dto = new SopAssignableCourseDto();
        dto.setId(d.getId());
        dto.setTitle(d.getTitle());
        dto.setVersion(d.getVersion());
        dto.setDepartmentTag(d.getDepartmentTag());
        dto.setOwnerName(d.getUploadedBy() == null ? null : name(d.getUploadedBy(), names));
        dto.setMine(userId.equals(d.getUploadedBy()));
        dto.setModuleCount(readiness.modules());
        dto.setApprovedModules(readiness.approvedModules());
        dto.setApprovedQuizzes(readiness.approvedQuizzes());
        dto.setReady(readiness.ready());
        dto.setVisibility(d.getVisibility() == null ? SopVisibility.PRIVATE : d.getVisibility());
        dto.setPassMark(d.getPassMark());
        dto.setDurationMinutes(d.effectiveDurationMinutes());
        dto.setDurationSet(d.getDurationMinutes() != null);
        dto.setEnrolledCount(enrolledCount);
        // FR-09-01: suggest the departments (and their job roles) that match the SOP's department.
        Set<Long> departments = new LinkedHashSet<>();
        Set<Long> roles = new LinkedHashSet<>();
        for (Staff s : staff) {
            OrgChart dept = department(s);
            if (dept != null && sameDepartment(d.getDepartmentTag(), dept.getName())) {
                departments.add(dept.getId());
                if (s.getRole() != null && s.getRole().isVisible()) {
                    roles.add(s.getRole().getId());
                }
            }
        }
        dto.setRecommendedDepartmentIds(new ArrayList<>(departments));
        dto.setRecommendedRoleIds(new ArrayList<>(roles));
        return dto;
    }

    private SopAssignmentDto toAssignmentDto(SopAssignment a, String courseTitle, Map<UUID, String> names) {
        SopAssignmentDto dto = new SopAssignmentDto();
        dto.setId(a.getId());
        dto.setSopDocumentId(a.getSopDocumentId());
        dto.setCourseTitle(courseTitle);
        dto.setAudience(a.getAudience());
        dto.setStaffCount(a.getStaffCount());
        dto.setMode(a.getMode());
        dto.setMandatory(a.isMandatory());
        dto.setEffectiveDate(a.getEffectiveDate());
        dto.setDeadline(a.getDeadline());
        dto.setDaysToComplete(a.getDaysToComplete());
        dto.setPassMark(a.getPassMark());
        dto.setAssignedByName(name(a.getAssignedBy(), names));
        dto.setCreatedAt(a.getCreatedAt());
        return dto;
    }

    private Map<UUID, SopEnrollment> enrollmentsByStaff(Long sopDocumentId) {
        return sopEnrollmentRepository.findAllBySopDocumentId(sopDocumentId).stream()
                .collect(Collectors.toMap(SopEnrollment::getStaffId, e -> e, (a, b) -> a));
    }

    private String name(UUID staffId, Map<UUID, String> cache) {
        return cache.computeIfAbsent(staffId,
                id -> staffRepository.findById(id).map(Staff::getName).orElse("Unknown user"));
    }

    /** A staff member's department: their job role's org-chart node, when it is a department. */
    static OrgChart department(Staff s) {
        OrgChart node = s.getRole() == null ? null : s.getRole().getOrgChart();
        return node != null && node.getType() == OrgChartType.D && !node.isDeleted() ? node : null;
    }

    private static List<Staff> byName(List<Staff> staff) {
        return staff.stream()
                .sorted(Comparator.comparing(s -> Objects.toString(s.getName(), ""), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static SopAudienceDto.Group group(Long id, String name, String departmentName) {
        SopAudienceDto.Group g = new SopAudienceDto.Group();
        g.setId(id);
        g.setName(name);
        g.setDepartmentName(departmentName);
        return g;
    }

    /** "Sales" matches "Sales Department"; "Front Office - Reception" matches "Front Office". */
    static boolean sameDepartment(String tag, String department) {
        String t = " " + departmentKey(tag) + " ";
        String d = " " + departmentKey(department) + " ";
        return !t.isBlank() && !d.isBlank() && (t.contains(d) || d.contains(t));
    }

    private static String departmentKey(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(department|dept|division|team|unit)\\b", " ")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}
