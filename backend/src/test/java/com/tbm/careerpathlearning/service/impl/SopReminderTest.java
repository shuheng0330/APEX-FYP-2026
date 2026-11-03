package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopCourseStatus;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Reminders for mandatory training at 09:00: 3 days ahead and once when overdue (FR-09-14, FR-11-16, FR-11-17). */
class SopReminderTest {

    private SopTestStore store;
    private SopReminderServiceImpl reminders;
    private SopLearningServiceImpl learning;
    private Staff aisyah;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        reminders = store.wire(new SopReminderServiceImpl());
        learning = store.wire(new SopLearningServiceImpl());
        aisyah = store.person("Nur Aisyah", null);
        store.doc(1L, "Cash Handling", store.person("Tan Wei Ming", null).getId());
        store.module(1L, 5);
        today = store.today(); // 12 Oct 2026
    }

    /** Aisyah's mandatory enrolment, from {@code start} with 14 days to complete and {@code lastDate} as the last date. */
    private SopEnrollment mandatory(SopAssignmentMode mode, LocalDate start, LocalDate lastDate) {
        SopEnrollment e = store.enrollment(1L, aisyah.getId(), mode, start, lastDate, 14, 70);
        e.setMandatory(true);
        return e;
    }

    private List<String> sent() {
        return store.notifications.stream().map(n -> n.type() + " | " + n.title() + " | " + n.body() + " | " + n.link()).toList();
    }

    @Test
    @DisplayName("Three days before a mandatory course is due, its learner is reminded once")
    void dueSoon() {
        mandatory(SopAssignmentMode.IMMEDIATE, today.minusDays(11), today.plusDays(30)); // due 15 Oct

        assertEquals(1, reminders.sendReminders());
        assertEquals(0, reminders.sendReminders(), "once");

        assertEquals(List.of("COURSE_DUE_SOON | Training due soon | Cash Handling is due on 15 Oct 2026. | /my-learning/course/1"), sent());
        assertEquals(aisyah.getId(), store.notifications.get(0).recipient());
    }

    @Test
    @DisplayName("Four days ahead is too early for a reminder")
    void notYet() {
        mandatory(SopAssignmentMode.IMMEDIATE, today.minusDays(10), today.plusDays(30)); // due 16 Oct

        assertEquals(0, reminders.sendReminders());
    }

    @Test
    @DisplayName("A course not started from the Learning Portal is reminded of its last date to start")
    void lastDayToStart() {
        mandatory(SopAssignmentMode.PORTAL, today, today.plusDays(2));

        reminders.sendReminders();

        assertEquals(List.of("COURSE_DUE_SOON | Start your training | The last day to start Cash Handling is 14 Oct 2026. "
                + "| /my-learning/course/1"), sent());
    }

    @Test
    @DisplayName("An overdue mandatory course stays open, shows as Overdue, and its learner is told once (FR-11-16)")
    void overdueOnce() {
        mandatory(SopAssignmentMode.IMMEDIATE, today.minusDays(20), today.plusDays(30)); // due 6 Oct

        reminders.sendReminders();
        store.clock.advance(Duration.ofDays(1));
        reminders.sendReminders();

        assertEquals(List.of("COURSE_OVERDUE | Training overdue | Cash Handling was due on 6 Oct 2026. It is still open, so please "
                + "finish it as soon as you can. | /my-learning/course/1"), sent());
        assertTrue(learning.course(1L, aisyah.getId()).getCourse().isOverdue());
        assertDoesNotThrow(() -> learning.start(1L, aisyah.getId()), "still open");
    }

    @Test
    @DisplayName("Not started by the last date to start is overdue too")
    void notStartedInTime() {
        mandatory(SopAssignmentMode.PORTAL, today.minusDays(10), today.minusDays(1));

        reminders.sendReminders();

        assertEquals(List.of("COURSE_OVERDUE | Training overdue | Cash Handling should have been started by 11 Oct 2026. It is still "
                + "open, so please finish it as soon as you can. | /my-learning/course/1"), sent());
    }

    @Test
    @DisplayName("Optional courses are never overdue and get no reminders (FR-11-17)")
    void optionalNever() {
        store.enrollment(1L, aisyah.getId(), SopAssignmentMode.IMMEDIATE, today.minusDays(20), today.plusDays(30), 14, 70);

        assertEquals(0, reminders.sendReminders());
        assertFalse(learning.course(1L, aisyah.getId()).getCourse().isOverdue());
    }

    @Test
    @DisplayName("No reminder for a finished, withdrawn, archived or not-yet-open course, or a staff member who left")
    void nothingToRemind() {
        mandatory(SopAssignmentMode.IMMEDIATE, today.minusDays(20), today.plusDays(30)).setStatus(SopEnrollmentStatus.COMPLETED);
        Staff ravi = store.person("Ravi Kumar", null);
        SopEnrollment withdrawn = store.enrollment(1L, ravi.getId(), SopAssignmentMode.IMMEDIATE, today.minusDays(20),
                today.plusDays(30), 14, 70);
        withdrawn.setMandatory(true);
        withdrawn.setStatus(SopEnrollmentStatus.WITHDRAWN);
        Staff farid = store.person("Ahmad Farid", null);
        store.enrollment(1L, farid.getId(), SopAssignmentMode.IMMEDIATE, today.plusDays(1), today.plusDays(30), 2, 70)
                .setMandatory(true);
        Staff left = store.person("Lim Ah Kow", null);
        left.setDeleted(true);
        store.enrollment(1L, left.getId(), SopAssignmentMode.IMMEDIATE, today.minusDays(20), today.plusDays(30), 14, 70)
                .setMandatory(true);
        store.doc(2L, "Old Course", null).setCourseStatus(SopCourseStatus.ARCHIVED);
        store.enrollment(2L, aisyah.getId(), SopAssignmentMode.IMMEDIATE, today.minusDays(20), today.plusDays(30), 14, 70)
                .setMandatory(true);

        assertEquals(0, reminders.sendReminders());
        assertTrue(store.notifications.isEmpty());
    }
}
