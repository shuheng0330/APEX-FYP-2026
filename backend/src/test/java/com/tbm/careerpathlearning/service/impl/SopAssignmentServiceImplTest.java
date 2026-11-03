package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopAssignableCourseDto;
import com.tbm.careerpathlearning.dto.SopAssignmentDto;
import com.tbm.careerpathlearning.dto.SopAssignmentPreviewDto;
import com.tbm.careerpathlearning.dto.SopAssignmentRequest;
import com.tbm.careerpathlearning.dto.SopAssignmentResultDto;
import com.tbm.careerpathlearning.dto.SopAudienceDto;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.SopVisibility;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SopAssignmentServiceImplTest {

    private SopTestStore store;
    private SopAssignmentServiceImpl service;
    private Staff aisyah, ravi, meiling, trainer;
    private final UUID otherTrainer = UUID.randomUUID();
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        service = store.wire(new SopAssignmentServiceImpl());
        today = store.today();

        OrgChart sales = store.department(10L, "Sales Department");
        OrgChart frontOffice = store.department(11L, "Front Office Department");
        OrgChart hr = store.department(12L, "HR Department");
        Role salesExecutive = store.role(20L, "Sales Executive", sales);
        Role receptionist = store.role(21L, "Receptionist", frontOffice);
        Role trainerRole = store.role(22L, "Trainer", hr);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        ravi = store.person("Ravi Kumar", salesExecutive);
        meiling = store.person("Chong Mei Ling", receptionist);
        trainer = store.person("Tan Wei Ming", trainerRole);

        // Course 1 is the trainer's and ready; course 2 still has material waiting for approval.
        store.doc(1L, "E-Hailing Delivery", trainer.getId()).setDepartmentTag("Sales");
        store.module(1L, 5);
        store.module(1L, 5);
        store.doc(2L, "Cash Handling", null).setDepartmentTag("Front Office - Reception");
        store.module(2L, 5);
        store.module(2L, ReviewStatus.PENDING, SopTestStore.quiz(5, ReviewStatus.APPROVED));
    }

    @Test
    @DisplayName("A trainer sees only their own courses, with departments and roles recommended from the SOP's tag")
    void trainerSeesOwnCourses() {
        List<SopAssignableCourseDto> own = service.listCourses(trainer.getId(), false);

        assertEquals(1, own.size());
        SopAssignableCourseDto course = own.get(0);
        assertEquals(1L, course.getId());
        assertTrue(course.isMine());
        assertTrue(course.isReady());
        assertEquals(2, course.getApprovedModules());
        assertEquals(2, course.getApprovedQuizzes());
        assertEquals(List.of(10L), course.getRecommendedDepartmentIds());
        assertEquals(List.of(20L), course.getRecommendedRoleIds());
        assertTrue(service.listCourses(otherTrainer, false).isEmpty());
    }

    @Test
    @DisplayName("Someone who can assign any training sees every course and which ones are not ready")
    void assignerSeesEveryCourse() {
        List<SopAssignableCourseDto> all = service.listCourses(otherTrainer, true);

        assertEquals(2, all.size());
        SopAssignableCourseDto cash = all.stream().filter(c -> c.getId() == 2L).findFirst().orElseThrow();
        assertFalse(cash.isReady());
        assertEquals(1, cash.getApprovedModules());
        assertEquals(2, cash.getApprovedQuizzes());
        assertEquals(List.of(11L), cash.getRecommendedDepartmentIds());
    }

    @Test
    @DisplayName("The audience lists departments, roles and people with staff counts")
    void audience() {
        SopAudienceDto audience = service.getAudience();

        assertEquals(3, audience.getDepartments().size());
        assertEquals(3, audience.getRoles().size());
        assertEquals(4, audience.getStaff().size());
        assertTrue(audience.getDepartments().stream()
                .anyMatch(g -> g.getName().equals("Sales Department") && g.getStaffCount() == 2));
    }

    @Test
    @DisplayName("Assigning a department plus one person enrols three, due days-to-complete after today")
    void assignDepartmentAndPerson() {
        SopAssignmentRequest request = salesDepartmentAndMeiling();

        SopAssignmentPreviewDto preview = service.preview(request);
        assertEquals(3, preview.getNewCount());
        assertEquals(3, preview.getStaff().size());

        SopAssignmentResultDto result = service.assign(request, trainer.getId(), false);
        assertEquals(3, result.getAssigned());
        assertEquals(0, result.getUpdated());
        assertEquals(3, result.getAssignment().getStaffCount());
        assertEquals("Sales Department; Chong Mei Ling", result.getAssignment().getAudience());

        SopEnrollment e = store.enrollmentOf(1L, aisyah.getId());
        assertEquals(today.plusDays(14), e.dueDate());
        assertTrue(e.isMandatory());
        assertEquals(80, e.getPassMark());
    }

    @Test
    @DisplayName("Newly assigned staff are notified with a link to the course and its due date (FR-09-05)")
    void assignmentNotifiesNewStaff() {
        service.assign(salesDepartmentAndMeiling(), trainer.getId(), false);

        assertEquals(3, store.notifications.size());
        SopTestStore.Sent toAisyah = store.notifications.stream()
                .filter(n -> n.recipient().equals(aisyah.getId())).findFirst().orElseThrow();
        assertEquals(NotificationType.COURSE_ASSIGNED, toAisyah.type());
        assertEquals("New mandatory training: E-Hailing Delivery", toAisyah.title());
        assertEquals("Tan Wei Ming assigned you \"E-Hailing Delivery\". Complete it by 26 Oct 2026.", toAisyah.body());
        assertEquals("/my-learning/course/1", toAisyah.link());
    }

    @Test
    @DisplayName("Staff whose enrolment changes are told the new terms; staff who completed it are not")
    void reassignmentNotifiesUpdates() {
        service.assign(salesDepartmentAndMeiling(), trainer.getId(), false);
        store.enrollmentOf(1L, aisyah.getId()).setStatus(SopEnrollmentStatus.COMPLETED);
        store.notifications.clear();
        SopAssignmentRequest portal = request(SopAssignmentMode.PORTAL, false, today.plusDays(60), 10, 70);
        portal.setRoleIds(List.of(20L)); // Aisyah, who completed it, and Ravi

        service.assign(portal, trainer.getId(), false);

        assertEquals(1, store.notifications.size());
        SopTestStore.Sent toRavi = store.notifications.get(0);
        assertEquals(ravi.getId(), toRavi.recipient());
        assertEquals(NotificationType.COURSE_ASSIGNMENT_UPDATED, toRavi.type());
        assertEquals("Training updated: E-Hailing Delivery", toRavi.title());
        assertEquals("The terms of \"E-Hailing Delivery\" have changed. It is in your Learning Portal now. Once you start, "
                + "you have 10 days to finish, and it must be finished by 11 Dec 2026.", toRavi.body());
    }

    @Test
    @DisplayName("Assigning again by job role updates the existing enrolments, and a near deadline caps the due date")
    void reassignByRole() {
        service.assign(salesDepartmentAndMeiling(), trainer.getId(), false);
        SopAssignmentRequest capped = request(SopAssignmentMode.IMMEDIATE, false, today.plusDays(5), 14, 70);
        capped.setRoleIds(List.of(20L));

        SopAssignmentResultDto result = service.assign(capped, otherTrainer, true);

        assertEquals(0, result.getAssigned());
        assertEquals(2, result.getUpdated());
        assertEquals(today.plusDays(5), store.enrollmentOf(1L, ravi.getId()).dueDate());
        assertEquals("Sales Executive (Sales Department)", result.getAssignment().getAudience());
    }

    @Test
    @DisplayName("From the portal, the due date is the deadline until the learner starts, then start plus days to complete")
    void portalDueDate() {
        SopAssignmentRequest portal = request(SopAssignmentMode.PORTAL, false, today.plusDays(60), 10, 70);
        portal.setStaffIds(List.of(meiling.getId()));
        service.assign(portal, trainer.getId(), false);
        SopEnrollment e = store.enrollmentOf(1L, meiling.getId());

        assertEquals(today.plusDays(60), e.dueDate());
        e.setStartedAt(store.now().plusDays(3));
        assertEquals(today.plusDays(13), e.dueDate());
    }

    @Test
    @DisplayName("At 1am in Malaysia (still the day before in UTC) the Malaysia date is today")
    void datesFollowMalaysiaTime() {
        store.clock.set(Instant.parse("2026-10-12T17:00:00Z")); // 13 Oct, 01:00 in Malaysia
        SopAssignmentRequest request = request(SopAssignmentMode.IMMEDIATE, false, LocalDate.of(2026, 11, 30), 14, 70);
        request.setStaffIds(List.of(aisyah.getId()));
        request.setEffectiveDate(LocalDate.of(2026, 10, 12));
        assertThrows(BadRequestException.class, () -> service.assign(request, trainer.getId(), false),
                "12 October is already in the past in Malaysia");

        request.setEffectiveDate(null);
        service.assign(request, trainer.getId(), false);
        SopEnrollment e = store.enrollmentOf(1L, aisyah.getId());

        assertEquals(LocalDate.of(2026, 10, 13), e.getEffectiveDate());
        assertEquals(LocalDate.of(2026, 10, 27), e.dueDate());
    }

    @Test
    @DisplayName("A portal due date counts from the Malaysia date the learner started, even when the start time is read back in UTC")
    void portalDueDateUsesTheMalaysiaStartDate() {
        SopEnrollment e = store.enrollment(1L, aisyah.getId(), SopAssignmentMode.PORTAL, LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 12, 31), 10, 70);
        e.setStartedAt(OffsetDateTime.parse("2026-10-12T17:30:00Z")); // 13 Oct, 01:30 in Malaysia

        assertEquals(LocalDate.of(2026, 10, 23), e.dueDate());
    }

    @Test
    @DisplayName("Staff who completed the course are left alone when it is assigned again")
    void completedStaffAreLeftAlone() {
        service.assign(salesDepartmentAndMeiling(), trainer.getId(), false);
        SopEnrollment completed = store.enrollmentOf(1L, aisyah.getId());
        completed.setStatus(SopEnrollmentStatus.COMPLETED);
        Long earlierAssignment = completed.getAssignmentId();
        SopAssignmentRequest everyone = request(SopAssignmentMode.PORTAL, false, today.plusDays(30), 14, 70);
        everyone.setEveryone(true);

        SopAssignmentPreviewDto preview = service.preview(everyone);
        assertEquals(1, preview.getNewCount(), "only the trainer is new");
        assertEquals(2, preview.getUpdateCount());
        assertEquals(1, preview.getCompletedCount());

        SopAssignmentResultDto result = service.assign(everyone, trainer.getId(), false);
        assertEquals(1, result.getAssigned());
        assertEquals(2, result.getUpdated());
        assertEquals(1, result.getSkippedCompleted());
        assertEquals(earlierAssignment, completed.getAssignmentId());
        assertEquals(SopEnrollmentStatus.COMPLETED, completed.getStatus());
    }

    @Test
    @DisplayName("Another trainer cannot assign or change the visibility of a course they do not own")
    void otherTrainersCannotAssign() {
        assertThrows(ForbiddenRequestException.class,
                () -> service.assign(salesDepartmentAndMeiling(), otherTrainer, false));
        assertThrows(ForbiddenRequestException.class,
                () -> service.setVisibility(1L, SopVisibility.OPEN, otherTrainer, false));
    }

    @Test
    @DisplayName("A course with material or quizzes awaiting approval cannot be assigned")
    void unapprovedCourseCannotBeAssigned() {
        SopAssignmentRequest pending = request(SopAssignmentMode.PORTAL, false, today.plusDays(30), 14, 70);
        pending.setSopDocumentId(2L);
        pending.setEveryone(true);

        assertThrows(BadRequestException.class, () -> service.assign(pending, otherTrainer, true));
    }

    @Test
    @DisplayName("Invalid terms and an empty audience are rejected")
    void invalidRequestsAreRejected() {
        SopAssignmentRequest bad = request(SopAssignmentMode.PORTAL, false, today.minusDays(1), 14, 70);
        bad.setEveryone(true);
        assertThrows(BadRequestException.class, () -> service.assign(bad, trainer.getId(), false), "deadline in the past");
        bad.setDeadline(today.plusDays(10));
        bad.setDaysToComplete(0);
        assertThrows(BadRequestException.class, () -> service.assign(bad, trainer.getId(), false), "0 days to complete");
        bad.setDaysToComplete(14);
        bad.setPassMark(101);
        assertThrows(BadRequestException.class, () -> service.assign(bad, trainer.getId(), false), "pass mark 101");
        bad.setPassMark(70);
        bad.setMode(null);
        assertThrows(BadRequestException.class, () -> service.assign(bad, trainer.getId(), false), "no mode");

        SopAssignmentRequest nobody = request(SopAssignmentMode.PORTAL, false, today.plusDays(10), 14, 70);
        assertThrows(BadRequestException.class, () -> service.assign(nobody, trainer.getId(), false), "no audience");
        assertTrue(store.assignments.isEmpty());
        assertTrue(store.notifications.isEmpty(), "a refused assignment notifies nobody");
    }

    @Test
    @DisplayName("Assigning only people who already completed the course is rejected")
    void audienceAlreadyCompleted() {
        service.assign(salesDepartmentAndMeiling(), trainer.getId(), false);
        store.enrollmentOf(1L, aisyah.getId()).setStatus(SopEnrollmentStatus.COMPLETED);
        SopAssignmentRequest done = request(SopAssignmentMode.PORTAL, false, today.plusDays(10), 14, 70);
        done.setStaffIds(List.of(aisyah.getId()));

        assertThrows(BadRequestException.class, () -> service.assign(done, trainer.getId(), false));
    }

    @Test
    @DisplayName("The history lists the course's assignments, newest first, to its owner only")
    void history() {
        service.assign(salesDepartmentAndMeiling(), trainer.getId(), false);
        store.clock.advance(Duration.ofHours(1));
        SopAssignmentRequest everyone = request(SopAssignmentMode.PORTAL, false, today.plusDays(30), 14, 70);
        everyone.setEveryone(true);
        service.assign(everyone, trainer.getId(), false);

        List<SopAssignmentDto> history = service.history(1L, trainer.getId(), false);

        assertEquals(2, history.size());
        assertEquals("Everyone", history.get(0).getAudience());
        assertEquals("Tan Wei Ming", history.get(0).getAssignedByName());
        assertTrue(service.history(1L, otherTrainer, false).isEmpty());
    }

    @Test
    @DisplayName("The owner can make a course Open")
    void setVisibility() {
        SopAssignableCourseDto open = service.setVisibility(1L, SopVisibility.OPEN, trainer.getId(), false);

        assertEquals(SopVisibility.OPEN, open.getVisibility());
        assertEquals(SopVisibility.OPEN, store.docs.get(1L).getVisibility());
    }

    @Test
    @DisplayName("Department tags match whole department names only")
    void departmentMatching() {
        assertTrue(SopAssignmentServiceImpl.sameDepartment("Sales", "Sales Department"));
        assertFalse(SopAssignmentServiceImpl.sameDepartment("IT", "Hospitality Department"));
        assertFalse(SopAssignmentServiceImpl.sameDepartment(null, "Sales Department"));
    }

    // --- fixtures ---------------------------------------------------------------

    /** Course 1 to the Sales Department and Chong Mei Ling: start immediately, 14 days, mandatory, pass mark 80. */
    private SopAssignmentRequest salesDepartmentAndMeiling() {
        SopAssignmentRequest r = request(SopAssignmentMode.IMMEDIATE, true, today.plusDays(30), 14, 80);
        r.setDepartmentIds(List.of(10L));
        r.setStaffIds(List.of(meiling.getId()));
        return r;
    }

    private static SopAssignmentRequest request(SopAssignmentMode mode, boolean mandatory, LocalDate deadline,
                                                int daysToComplete, int passMark) {
        SopAssignmentRequest r = new SopAssignmentRequest();
        r.setSopDocumentId(1L);
        r.setMode(mode);
        r.setMandatory(mandatory);
        r.setDeadline(deadline);
        r.setDaysToComplete(daysToComplete);
        r.setPassMark(passMark);
        return r;
    }
}
