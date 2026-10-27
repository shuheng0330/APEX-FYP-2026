package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopCompetencyScoreDto;
import com.tbm.careerpathlearning.dto.SopEnrollmentProgressDto;
import com.tbm.careerpathlearning.dto.SopScoreboardDto;
import com.tbm.careerpathlearning.dto.SopScoreboardRecordDto;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SopScoreboardServiceImplTest {

    private SopTestStore store;
    private SopScoreboardServiceImpl service;
    private final UUID trainer = UUID.randomUUID();
    private final UUID otherTrainer = UUID.randomUUID();
    private Staff aisyah, ravi;
    private SopEnrollment aisyahCash, raviCash, aisyahForklift;

    /**
     * Cash Handling (the trainer's, 2 modules): Aisyah completed it after failing module 1 once, Ravi failed
     * module 1, Mei Ling passed module 1, Farid has not started and is past due, and a former staff member is
     * still enrolled. Forklift Safety belongs to another trainer; Aisyah failed its module 1.
     */
    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        service = store.wire(new SopScoreboardServiceImpl());
        OrgChart sales = store.department(10L, "Sales Department");
        OrgChart frontOffice = store.department(11L, "Front Office Department");
        Role salesExecutive = store.role(20L, "Sales Executive", sales);
        Role receptionist = store.role(21L, "Receptionist", frontOffice);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        ravi = store.person("Ravi Kumar", salesExecutive);
        Staff meiling = store.person("Chong Mei Ling", receptionist);
        Staff farid = store.person("Ahmad Farid", receptionist);

        store.doc(1L, "Cash Handling", trainer);
        Long m1 = store.module(1L, 4).getId();
        Long m2 = store.module(1L, 4).getId();
        store.doc(2L, "Forklift Safety", otherTrainer);
        Long f1 = store.module(2L, 4).getId();
        store.doc(3L, "Data Privacy", trainer);
        store.module(3L, 4);

        aisyahCash = enrolled(1L, aisyah.getId(), SopEnrollmentStatus.COMPLETED, SopAssignmentMode.PORTAL, 0);
        store.attempt(aisyahCash, m1, 50, false, daysAgo(3));
        store.attempt(aisyahCash, m1, 75, true, daysAgo(2));
        store.attempt(aisyahCash, m2, 100, true, daysAgo(1));
        aisyahCash.setFinalScore(88);
        aisyahCash.setCompletedAt(daysAgo(1));
        raviCash = enrolled(1L, ravi.getId(), SopEnrollmentStatus.IN_PROGRESS, SopAssignmentMode.PORTAL, 0);
        store.attempt(raviCash, m1, 25, false, daysAgo(1));
        SopEnrollment meilingCash = enrolled(1L, meiling.getId(), SopEnrollmentStatus.IN_PROGRESS, SopAssignmentMode.PORTAL, 0);
        store.attempt(meilingCash, m1, 100, true, daysAgo(1));
        enrolled(1L, farid.getId(), SopEnrollmentStatus.NOT_STARTED, SopAssignmentMode.IMMEDIATE, 20).setMandatory(true); // only mandatory is overdue
        enrolled(1L, UUID.randomUUID(), SopEnrollmentStatus.NOT_STARTED, SopAssignmentMode.PORTAL, 0);
        aisyahForklift = enrolled(2L, aisyah.getId(), SopEnrollmentStatus.IN_PROGRESS, SopAssignmentMode.PORTAL, 0);
        store.attempt(aisyahForklift, f1, 40, false, daysAgo(1));
    }

    @Test
    @DisplayName("A trainer sees the enrolments on their own courses, sorted by name, with filter options")
    void trainerSeesOwnCourses() {
        SopScoreboardDto mine = service.scoreboard(trainer, false);

        assertEquals(5, mine.getRecords().size());
        assertTrue(mine.getRecords().stream().allMatch(r -> r.getSopDocumentId() == 1L));
        assertEquals(1, mine.getCourses().size(), "only courses with enrolments are filter options");
        assertEquals("Cash Handling", mine.getCourses().get(0).getTitle());
        assertEquals(List.of("Front Office Department", "Sales Department"), mine.getDepartments());
        assertEquals("Ahmad Farid", mine.getRecords().get(0).getStaffName());
    }

    @Test
    @DisplayName("Someone who can assign any training sees every course; another trainer only their own")
    void scope() {
        SopScoreboardDto all = service.scoreboard(otherTrainer, true);

        assertEquals(6, all.getRecords().size());
        assertEquals(2, all.getCourses().size());
        assertEquals(1, service.scoreboard(otherTrainer, false).getRecords().size());
    }

    @Test
    @DisplayName("A completed enrolment shows the final score, attempts and the learner's department and role")
    void completedRecord() {
        SopScoreboardRecordDto r = find("Nur Aisyah");

        assertEquals("COMPLETED", r.getStatus());
        assertEquals(88, r.getScore());
        assertEquals(88, r.getFinalScore());
        assertEquals(2, r.getModulesPassed());
        assertEquals(2, r.getModuleCount());
        assertEquals(3, r.getAttempts());
        assertEquals(1, r.getRetrainingAttempts());
        assertEquals("Sales Department", r.getDepartment());
        assertEquals("Sales Executive", r.getJobRole());
        assertEquals(daysAgo(1).toInstant(), r.getLastAttemptAt().toInstant(), "the most recent attempt");
        assertFalse(r.isOverdue(), "a completed course is never overdue");
    }

    @Test
    @DisplayName("Statuses: a failed quiz is retraining, partial progress averages the passed modules, a late start is overdue")
    void inProgressStatuses() {
        SopScoreboardRecordDto failed = find("Ravi Kumar");
        assertEquals("RETRAINING", failed.getStatus());
        assertNull(failed.getScore());
        assertEquals(1, failed.getRetrainingAttempts());

        SopScoreboardRecordDto partial = find("Chong Mei Ling");
        assertEquals("IN_PROGRESS", partial.getStatus());
        assertEquals(100, partial.getScore());
        assertEquals(1, partial.getModulesPassed());

        SopScoreboardRecordDto late = find("Ahmad Farid");
        assertEquals("NOT_STARTED", late.getStatus());
        assertTrue(late.isOverdue());
        assertEquals(SopAssignmentMode.IMMEDIATE, late.getMode());
    }

    @Test
    @DisplayName("An enrolment of someone no longer on staff is still listed")
    void formerStaff() {
        SopScoreboardRecordDto gone = find("Former staff member");

        assertNotNull(gone);
        assertNull(gone.getDepartment());
    }

    @Test
    @DisplayName("The drill-down shows each module's state and attempts")
    void drillDown() {
        SopEnrollmentProgressDto completed = service.enrollment(aisyahCash.getId(), trainer, false);
        SopEnrollmentProgressDto.Module first = completed.getModules().get(0);
        assertEquals(2, first.getAttempts().size());
        assertFalse(first.getAttempts().get(0).isPassed());
        assertTrue(first.getAttempts().get(1).isPassed());
        assertEquals(75, first.getPassedScore());
        assertEquals("PASSED", first.getState());

        SopEnrollmentProgressDto failed = service.enrollment(raviCash.getId(), trainer, false);
        assertEquals("RETRAINING", failed.getModules().get(0).getState());
        assertEquals("LOCKED", failed.getModules().get(1).getState());
    }

    @Test
    @DisplayName("A trainer cannot open an enrolment on another trainer's course; someone who can assign any training can")
    void drillDownScope() {
        assertThrows(ForbiddenRequestException.class, () -> service.enrollment(aisyahForklift.getId(), trainer, false));
        assertEquals("RETRAINING", service.enrollment(aisyahForklift.getId(), trainer, true).getRecord().getStatus());
    }

    @Test
    @DisplayName("Only completed courses are sent to the appraisal module")
    void competencyScores() {
        List<SopCompetencyScoreDto> scores = service.competencyScores(aisyah.getId());

        assertEquals(1, scores.size());
        assertEquals(88, scores.get(0).getFinalScore());
        assertEquals(String.format("APEX-SOP-1-%05d", aisyahCash.getId()), scores.get(0).getCertificateNo());
        assertTrue(service.competencyScores(ravi.getId()).isEmpty());
    }

    // --- fixtures ---------------------------------------------------------------

    private SopScoreboardRecordDto find(String name) {
        return service.scoreboard(trainer, false).getRecords().stream()
                .filter(r -> r.getStaffName().equals(name)).findFirst().orElse(null);
    }

    private OffsetDateTime daysAgo(int days) {
        return store.now().minusDays(days);
    }

    private SopEnrollment enrolled(long docId, UUID staffId, SopEnrollmentStatus status, SopAssignmentMode mode,
                                   int effectiveDaysAgo) {
        SopEnrollment e = store.enrollment(docId, staffId, mode, store.today().minusDays(effectiveDaysAgo), null, 14, 70);
        e.setStatus(status);
        e.setAssignedAt(daysAgo(effectiveDaysAgo));
        if (status != SopEnrollmentStatus.NOT_STARTED) {
            e.setStartedAt(daysAgo(5));
        }
        return e;
    }
}
