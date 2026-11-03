package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.SopAssignment;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.repository.SopAssignmentRepository;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopEnrollmentRepository;
import com.tbm.careerpathlearning.repository.StaffRepository;
import com.tbm.careerpathlearning.service.NotificationService;
import com.tbm.careerpathlearning.service.SopCourseReadiness;
import com.tbm.careerpathlearning.service.SopVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Assigns new joiners (FR-09-12): active staff who are now in an assignment's departments or job roles, or anyone for an
 * everyone assignment, and who have never been enrolled in any version of its course. They get the course's live
 * version from the day they are found, due that day plus the days to complete. Nobody is added once the course is
 * archived, while it can't be assigned (FR-09-10), or for assignments made before step 3.4 or with the option off.
 */
@Service
public class SopNewJoiners {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    @Autowired private SopAssignmentRepository sopAssignmentRepository;
    @Autowired private SopDocumentRepository sopDocumentRepository;
    @Autowired private SopEnrollmentRepository sopEnrollmentRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private SopVersionService sopVersionService;
    @Autowired private SopCourseReadiness sopCourseReadiness;
    @Autowired private NotificationService notificationService;
    @Autowired private Clock clock;

    /** Everyone who has joined an audience since its assignment: the daily 09:00 run. */
    @Transactional
    public int includeAll() {
        return include(null);
    }

    /** Just this person, when they open My Learning, so a new joiner sees the course straight away. */
    @Transactional
    public int includeFor(UUID staffId) {
        return include(staffId);
    }

    private int include(UUID only) {
        List<SopAssignment> assignments = sopAssignmentRepository.findAllByIncludeNewJoinersTrueOrderByCreatedAtDesc();
        if (assignments.isEmpty()) {
            return 0;
        }
        List<Staff> active = staffRepository.findAllActiveWithRole().stream()
                .filter(s -> !s.isDeleted() && (only == null || s.getId().equals(only))).toList();
        LocalDate today = LocalDate.now(clock);
        OffsetDateTime now = OffsetDateTime.now(clock);
        int added = 0;
        // Newest first: when several assignments of a course take someone in, the latest one's terms apply.
        for (SopAssignment a : assignments) {
            // Who could join it, before any course is loaded: usually nobody, when one person opens My Learning.
            List<Staff> candidates = active.stream().filter(s -> inAudience(a, s)).toList();
            if (candidates.isEmpty()) {
                continue;
            }
            SopDocument assigned = sopDocumentRepository.findById(a.getSopDocumentId()).orElse(null);
            if (assigned == null || assigned.isDeleted()) {
                continue;
            }
            List<SopDocument> versions = sopVersionService.versionsOf(assigned);
            SopDocument live = versions.stream().filter(SopDocument::isLive).findFirst().orElse(null);
            if (live == null || !sopCourseReadiness.of(live).ready()) {
                continue;
            }
            Set<UUID> enrolled = sopEnrollmentRepository.findAllBySopDocumentIdIn(versions.stream().map(SopDocument::getId).toList())
                    .stream().map(SopEnrollment::getStaffId).collect(Collectors.toSet());
            List<SopEnrollment> joined = new ArrayList<>();
            for (Staff s : candidates) {
                if (!enrolled.contains(s.getId())) {
                    joined.add(enrolment(a, live, s, today, now));
                }
            }
            if (joined.isEmpty()) {
                continue;
            }
            sopEnrollmentRepository.saveAll(joined);
            a.setStaffCount(a.getStaffCount() + joined.size());
            sopAssignmentRepository.save(a);
            String link = "/my-learning/course/" + live.getId();
            for (SopEnrollment e : joined) {
                notificationService.notify(e.getStaffId(), NotificationType.COURSE_ASSIGNED,
                        (e.isMandatory() ? "New mandatory training: " : "New training: ") + live.getTitle(),
                        "\"" + live.getTitle() + "\" is assigned to " + a.getAudience() + ", which now includes you. " + terms(e), link);
            }
            added += joined.size();
        }
        return added;
    }

    private static boolean inAudience(SopAssignment a, Staff s) {
        if (a.isAudienceEveryone()) {
            return true;
        }
        OrgChart department = SopAssignmentServiceImpl.department(s);
        return (s.getRole() != null && ids(a.getAudienceRoleIds()).contains(s.getRole().getId()))
                || (department != null && ids(a.getAudienceDepartmentIds()).contains(department.getId()));
    }

    /**
     * From the day they are found (or the assignment's own start, if later), due that day plus the days to complete.
     * The last date never comes sooner than that, and never sooner than everyone else's.
     */
    private static SopEnrollment enrolment(SopAssignment a, SopDocument live, Staff s, LocalDate today, OffsetDateTime now) {
        LocalDate start = a.getEffectiveDate() != null && a.getEffectiveDate().isAfter(today) ? a.getEffectiveDate() : today;
        LocalDate due = start.plusDays(a.getDaysToComplete());
        SopEnrollment e = new SopEnrollment();
        e.setSopDocumentId(live.getId());
        e.setStaffId(s.getId());
        e.setAssignmentId(a.getId());
        e.setMode(a.getMode());
        e.setMandatory(a.isMandatory());
        e.setEffectiveDate(start);
        e.setDeadline(a.getDeadline() != null && a.getDeadline().isAfter(due) ? a.getDeadline() : due);
        e.setDaysToComplete(a.getDaysToComplete());
        e.setPassMark(Math.max(a.getPassMark(), live.getPassMark()));
        e.setDurationMinutes(live.effectiveDurationMinutes());
        e.setStatus(SopEnrollmentStatus.NOT_STARTED);
        e.setAssignedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    private static String terms(SopEnrollment e) {
        return e.getMode() == SopAssignmentMode.IMMEDIATE
                ? "Complete it by " + DATE.format(e.dueDate()) + "."
                : "It is in your Learning Portal now. Start it by " + DATE.format(e.getDeadline()) + "; once you start, you have "
                        + e.getDaysToComplete() + " days to finish.";
    }

    private static Set<Long> ids(String csv) {
        return csv == null || csv.isBlank() ? Set.of()
                : Arrays.stream(csv.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(Long::valueOf).collect(Collectors.toSet());
    }
}
