package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopAssignmentDto;
import com.tbm.careerpathlearning.dto.SopAssignmentRequest;
import com.tbm.careerpathlearning.dto.SopLearningCourseDto;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopCourseStatus;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** A department, job-role or everyone assignment also reaches people who join later (FR-09-12). */
class SopNewJoinersTest {

    private SopTestStore store;
    private SopAssignmentServiceImpl assignments;
    private SopLearningServiceImpl learning;
    private SopNewJoiners joiners;
    private Role salesExecutive, receptionist;
    private Staff trainer, aisyah;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        assignments = store.wire(new SopAssignmentServiceImpl());
        learning = store.wire(new SopLearningServiceImpl());
        joiners = store.newJoiners();
        OrgChart sales = store.department(10L, "Sales Department");
        salesExecutive = store.role(20L, "Sales Executive", sales);
        receptionist = store.role(21L, "Receptionist", store.department(11L, "Front Office"));
        trainer = store.person("Tan Wei Ming", null);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        store.doc(1L, "Cash Handling", trainer.getId());
        store.module(1L, 5);
        store.module(1L, 5);
        today = store.today(); // 12 Oct 2026
    }

    /** Cash Handling for the Sales Department: start immediately, mandatory, 14 days, by 11 Nov. */
    private SopAssignmentRequest toSales(Boolean includeNewJoiners) {
        SopAssignmentRequest r = new SopAssignmentRequest();
        r.setSopDocumentId(1L);
        r.setDepartmentIds(List.of(10L));
        r.setMode(SopAssignmentMode.IMMEDIATE);
        r.setMandatory(true);
        r.setEffectiveDate(today);
        r.setDeadline(today.plusDays(30));
        r.setDaysToComplete(14);
        r.setIncludeNewJoiners(includeNewJoiners);
        return r;
    }

    private SopAssignmentDto assign(SopAssignmentRequest r) {
        return assignments.assign(r, trainer.getId(), false).getAssignment();
    }

    private SopEnrollment enrolmentOf(Staff s) {
        return store.enrollments.values().stream().filter(e -> e.getStaffId().equals(s.getId())).findFirst().orElse(null);
    }

    @Test
    @DisplayName("Someone who joins the department later gets the course from that day, due that day plus the days to complete")
    void joinerIsAssigned() {
        SopAssignmentDto assigned = assign(toSales(null));
        assertTrue(assigned.isIncludeNewJoiners(), "on unless the trainer opts out");
        Staff siti = store.person("Siti Aminah", salesExecutive);
        store.clock.advance(Duration.ofDays(2));

        assertEquals(1, joiners.includeAll());
        assertEquals(0, joiners.includeAll(), "once");

        SopEnrollment e = enrolmentOf(siti);
        assertEquals(today.plusDays(2), e.getEffectiveDate());
        assertEquals(today.plusDays(16), e.dueDate(), "join day plus 14 days");
        assertTrue(e.isMandatory());
        assertEquals(assigned.getId(), e.getAssignmentId());
        assertEquals(SopEnrollmentStatus.NOT_STARTED, e.getStatus());
        assertEquals(2, store.assignments.get(assigned.getId()).getStaffCount(), "Aisyah, then Siti");
        SopTestStore.Sent sent = store.notifications.get(store.notifications.size() - 1);
        assertEquals(siti.getId(), sent.recipient());
        assertEquals(NotificationType.COURSE_ASSIGNED, sent.type());
        assertEquals("New mandatory training: Cash Handling", sent.title());
        assertEquals("\"Cash Handling\" is assigned to Sales Department, which now includes you. Complete it by 28 Oct 2026.",
                sent.body());
    }

    @Test
    @DisplayName("A new joiner sees the course as soon as they open My Learning, before the next daily run")
    void seenOnOpeningMyLearning() {
        assign(toSales(null));
        Staff siti = store.person("Siti Aminah", salesExecutive);

        List<SopLearningCourseDto> courses = learning.home(siti.getId()).getCourses();

        assertEquals(List.of(1L), courses.stream().map(SopLearningCourseDto::getSopDocumentId).toList());
    }

    @Test
    @DisplayName("If the new-joiner check fails, My Learning still opens; the daily run adds them later")
    void portalOpensAnyway() {
        assign(toSales(null));
        ReflectionTestUtils.setField(learning, "sopNewJoiners", new SopNewJoiners() {
            @Override
            public int includeFor(UUID staffId) {
                throw new IllegalStateException("added at the same moment by the daily run");
            }
        });

        assertEquals(List.of(1L), learning.home(aisyah.getId()).getCourses().stream()
                .map(SopLearningCourseDto::getSopDocumentId).toList());
    }

    @Test
    @DisplayName("Moving into the department counts as joining it; an inactive account does not")
    void movingIn() {
        Staff ravi = store.person("Ravi Kumar", receptionist);
        Staff gone = store.person("Lim Ah Kow", salesExecutive);
        gone.setAccountStatus(StaffAccountStatus.INACTIVE);
        assign(toSales(null));
        assertNull(enrolmentOf(ravi));

        ravi.setRole(salesExecutive);

        assertEquals(1, joiners.includeAll());
        assertNotNull(enrolmentOf(ravi));
        assertNull(enrolmentOf(gone));
    }

    @Test
    @DisplayName("A job-role assignment takes in people given that role later")
    void jobRole() {
        SopAssignmentRequest r = toSales(null);
        r.setDepartmentIds(List.of());
        r.setRoleIds(List.of(21L));
        store.person("Ahmad Farid", receptionist);
        assign(r);
        Staff meiling = store.person("Chong Mei Ling", receptionist);

        assertEquals(1, joiners.includeAll());
        assertNotNull(enrolmentOf(meiling));
    }

    @Test
    @DisplayName("Nobody is added when the trainer opted out, or when only named people were chosen")
    void optedOutOrNamedPeople() {
        assertFalse(assign(toSales(false)).isIncludeNewJoiners());
        SopAssignmentRequest named = toSales(true);
        named.setDepartmentIds(List.of());
        named.setStaffIds(List.of(aisyah.getId()));
        assertFalse(assign(named).isIncludeNewJoiners(), "named people are not an audience anyone can join");
        Staff siti = store.person("Siti Aminah", salesExecutive);

        assertEquals(0, joiners.includeAll());
        assertNull(enrolmentOf(siti));
    }

    @Test
    @DisplayName("Anyone enrolled before, in any version, finished or withdrawn, is never added again")
    void neverTwice() {
        assign(toSales(null));
        enrolmentOf(aisyah).setStatus(SopEnrollmentStatus.WITHDRAWN);

        assertEquals(0, joiners.includeAll());
    }

    @Test
    @DisplayName("Nobody joins while the course is archived or can't be assigned; they join once it can be again")
    void onlyWhileTheCourseCanBeAssigned() {
        assign(toSales(null));
        Staff siti = store.person("Siti Aminah", salesExecutive);
        SopDocument course = store.docs.get(1L);

        course.setCourseStatus(SopCourseStatus.ARCHIVED);
        assertEquals(0, joiners.includeAll());
        course.setCourseStatus(SopCourseStatus.LIVE);
        SopModule first = store.modules.values().iterator().next();
        store.questionsOf(first.getId()).get(0).setReviewStatus(ReviewStatus.PENDING); // being edited
        assertEquals(0, joiners.includeAll());
        store.questionsOf(first.getId()).get(0).setReviewStatus(ReviewStatus.APPROVED);

        assertEquals(1, joiners.includeAll());
        assertNotNull(enrolmentOf(siti));
    }

    @Test
    @DisplayName("After a new version goes live, a new joiner gets that version")
    void newVersion() {
        assign(toSales(null));
        store.docs.get(1L).setCourseStatus(SopCourseStatus.SUPERSEDED);
        SopDocument v2 = store.doc(2L, "Cash Handling", trainer.getId());
        v2.setCourseId(1L);
        v2.setVersionNo(2);
        store.module(2L, 5);
        Staff siti = store.person("Siti Aminah", salesExecutive);

        assertEquals(1, joiners.includeAll());
        assertEquals(2L, enrolmentOf(siti).getSopDocumentId());
    }

    @Test
    @DisplayName("Someone joining near the deadline still gets the full days to complete")
    void lateJoiner() {
        SopAssignmentRequest r = toSales(null);
        r.setDeadline(today.plusDays(5));
        assign(r);
        assertEquals(today.plusDays(5), enrolmentOf(aisyah).dueDate(), "capped for everyone assigned at the time");
        store.clock.advance(Duration.ofDays(4));
        Staff siti = store.person("Siti Aminah", salesExecutive);

        joiners.includeAll();

        assertEquals(today.plusDays(18), enrolmentOf(siti).dueDate());
    }

    @Test
    @DisplayName("Assignments made before this change are left alone")
    void olderAssignments() {
        assign(toSales(null));
        store.assignments.values().forEach(a -> a.setIncludeNewJoiners(false)); // as the column's default leaves them
        store.person("Siti Aminah", salesExecutive);

        assertEquals(0, joiners.includeAll());
        assertEquals(List.of(aisyah.getId()), store.enrollments.values().stream().map(SopEnrollment::getStaffId).toList());
    }
}
