package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopScoreboardDto;
import com.tbm.careerpathlearning.dto.SopScoreboardRecordDto;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.StaffAccountStatus;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Who sees what on the Training Scoreboard: a manager their direct reports (FR-10-10, decision C); Certified only at
 * the pass mark that applies (FR-10-11); staff who left listed but not counted (FR-10-12).
 */
class SopScoreboardScopeTest {

    private SopTestStore store;
    private SopScoreboardServiceImpl scoreboard;
    private SopLearningServiceImpl learning;
    private TrainerAccessServiceImpl trainerAccess;
    private Staff trainer, jason, aisyah, ravi, sarah, meiling;
    private SopEnrollment aisyahCash, raviForklift, meilingCash;
    private SopModule cashFirst;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        scoreboard = store.wire(new SopScoreboardServiceImpl());
        learning = store.wire(new SopLearningServiceImpl());
        trainerAccess = store.wire(new TrainerAccessServiceImpl());
        OrgChart sales = store.department(10L, "Sales Department");
        Role executive = store.role(20L, "Sales Executive", sales);
        Role supervisor = store.role(21L, "Outlet Supervisor", sales);
        trainer = store.person("Tan Wei Ming", null);
        jason = store.person("Jason Lim", supervisor);
        aisyah = store.person("Nur Aisyah", executive);
        ravi = store.person("Ravi Kumar", executive);
        sarah = store.person("Sarah Wong", supervisor);
        meiling = store.person("Chong Mei Ling", executive);
        aisyah.setManager(jason);
        ravi.setManager(jason);
        sarah.setManager(jason);    // Sarah reports to Jason ...
        meiling.setManager(sarah);  // ... and Mei Ling to Sarah, so Mei Ling is not one of Jason's direct reports

        // Cash Handling is the trainer's; Forklift Safety belongs to someone else.
        store.doc(1L, "Cash Handling", trainer.getId());
        cashFirst = store.module(1L, 5);
        store.module(1L, 5);
        store.doc(2L, "Forklift Safety", store.person("Other Trainer", null).getId());
        store.module(2L, 5);
        aisyahCash = enrol(1L, aisyah);
        meilingCash = enrol(1L, meiling);
        raviForklift = enrol(2L, ravi);
        enrol(2L, sarah);
    }

    private SopEnrollment enrol(long docId, Staff s) {
        return store.enrollment(docId, s.getId(), SopAssignmentMode.IMMEDIATE, store.today(), store.today().plusDays(30), 14, 70);
    }

    private static List<String> rows(SopScoreboardDto dto) {
        return dto.getRecords().stream().map(r -> r.getStaffName() + " / " + r.getCourseTitle()).toList();
    }

    @Test
    @DisplayName("A manager sees their direct reports' training on every course, and nobody else's (FR-10-10, decision C)")
    void managerSeesDirectReports() {
        SopScoreboardDto dto = scoreboard.scoreboard(jason.getId(), false, true);

        assertEquals("TEAM", dto.getScope());
        assertEquals(List.of("Nur Aisyah / Cash Handling", "Ravi Kumar / Forklift Safety", "Sarah Wong / Forklift Safety"), rows(dto));
        assertEquals(List.of("Cash Handling", "Forklift Safety"), dto.getCourses().stream().map(SopScoreboardDto.Course::getTitle).toList());
    }

    @Test
    @DisplayName("A manager who is also a trainer sees their own courses and their team")
    void trainerAndManager() {
        Staff farid = store.person("Ahmad Farid", null);
        farid.setManager(trainer);
        enrol(2L, farid);

        SopScoreboardDto dto = scoreboard.scoreboard(trainer.getId(), false, true);

        assertEquals("OWN_AND_TEAM", dto.getScope());
        assertEquals(List.of("Ahmad Farid / Forklift Safety", "Chong Mei Ling / Cash Handling", "Nur Aisyah / Cash Handling"), rows(dto));
        assertEquals("OWN", scoreboard.scoreboard(trainer.getId(), false, false).getScope());
    }

    @Test
    @DisplayName("Only HR and the Super Admin, and a manager for their reports, get the link to a whole training record (FR-18-01)")
    void trainingRecordLink() {
        Staff farid = store.person("Ahmad Farid", null);
        farid.setManager(trainer);
        enrol(1L, farid);

        assertTrue(scoreboard.scoreboard(UUID.randomUUID(), true, false).getRecords().stream()
                .allMatch(SopScoreboardRecordDto::isCanOpenRecord));
        assertTrue(scoreboard.scoreboard(jason.getId(), false, true).getRecords().stream()
                .allMatch(SopScoreboardRecordDto::isCanOpenRecord));
        List<String> trainers = scoreboard.scoreboard(trainer.getId(), false, true).getRecords().stream()
                .filter(SopScoreboardRecordDto::isCanOpenRecord).map(SopScoreboardRecordDto::getStaffName).toList();
        assertEquals(List.of("Ahmad Farid"), trainers); // the trainer's own report, not every learner on the course
    }

    @Test
    @DisplayName("A manager opens their reports' details only")
    void managerDetails() {
        assertEquals("Ravi Kumar", scoreboard.enrollment(raviForklift.getId(), jason.getId(), false, true).getRecord().getStaffName());

        ForbiddenRequestException e = assertThrows(ForbiddenRequestException.class,
                () -> scoreboard.enrollment(meilingCash.getId(), jason.getId(), false, true));
        assertEquals("You can only view progress on your team's training or courses you created.", e.getMessage());
    }

    @Test
    @DisplayName("Sign-in marks someone with active direct reports as a manager")
    void whoIsAManager() {
        assertTrue(trainerAccess.hasTeam(jason.getId()));
        assertTrue(trainerAccess.hasTeam(sarah.getId()));
        assertFalse(trainerAccess.hasTeam(aisyah.getId()));

        meiling.setAccountStatus(StaffAccountStatus.INACTIVE);
        assertFalse(trainerAccess.hasTeam(sarah.getId()), "only active staff count");
    }

    @Test
    @DisplayName("Someone who left is still listed, marked so the figures leave them out (FR-10-12)")
    void leaversMarked() {
        ravi.setAccountStatus(StaffAccountStatus.INACTIVE);
        sarah.setDeleted(true);

        List<SopScoreboardRecordDto> records = scoreboard.scoreboard(trainer.getId(), true, false).getRecords();

        assertEquals(4, records.size(), "everyone stays listed");
        assertEquals(List.of("Chong Mei Ling", "Nur Aisyah"), records.stream().filter(r -> !r.isLeftCompany())
                .map(SopScoreboardRecordDto::getStaffName).toList());
    }

    @Test
    @DisplayName("A module passed below a pass mark raised since counts only once passed at the new mark (FR-10-11)")
    void passedAtTheMarkThatApplies() {
        store.attempt(aisyahCash, cashFirst.getId(), 75, true, store.now()); // passed when the pass mark was 70
        aisyahCash.setStatus(SopEnrollmentStatus.IN_PROGRESS);
        assertEquals(1, scoreboard.scoreboard(trainer.getId(), false).getRecords().stream()
                .filter(r -> r.getStaffName().equals("Nur Aisyah")).findFirst().orElseThrow().getModulesPassed());

        aisyahCash.setPassMark(80); // a later assignment raised it

        SopScoreboardRecordDto record = scoreboard.scoreboard(trainer.getId(), false).getRecords().stream()
                .filter(r -> r.getStaffName().equals("Nur Aisyah")).findFirst().orElseThrow();
        assertEquals(0, record.getModulesPassed());
        assertEquals("RETRAINING", record.getStatus());
        assertEquals("RETRAINING", learning.course(1L, aisyah.getId()).getModules().get(0).getState());
    }
}
