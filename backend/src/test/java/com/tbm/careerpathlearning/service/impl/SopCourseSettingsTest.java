package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopAssignmentRequest;
import com.tbm.careerpathlearning.dto.SopCourseSettingsRequest;
import com.tbm.careerpathlearning.dto.SopDocumentDto;
import com.tbm.careerpathlearning.dto.SopLearningCourseDto;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopVisibility;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Each course's own pass mark and estimated duration (FR-07-15, FR-09-03, P2-01 to P2-04). */
class SopCourseSettingsTest {

    private SopTestStore store;
    private SopServiceImpl sopService;
    private SopAssignmentServiceImpl assignments;
    private SopLearningServiceImpl learning;
    private Staff trainer, aisyah;
    private SopViewer owner;
    private SopDocument course;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        sopService = store.wire(new SopServiceImpl());
        assignments = store.wire(new SopAssignmentServiceImpl());
        learning = store.wire(new SopLearningServiceImpl());
        trainer = store.person("Tan Wei Ming", null);
        aisyah = store.person("Nur Aisyah", null);
        owner = new SopViewer(trainer.getId(), false);
        course = store.doc(1L, "Cash Handling", trainer.getId());
        store.module(1L, 2);
    }

    @Test
    @DisplayName("A new course has a 70% pass mark and counts 1 hour until the trainer sets its duration")
    void defaults() {
        SopDocumentDto dto = sopService.getDetail(1L, owner).getDocument();

        assertEquals(70, dto.getPassMark());
        assertEquals(60, dto.getDurationMinutes());
        assertFalse(dto.isDurationSet(), "the trainer is reminded until they set it");
    }

    @Test
    @DisplayName("The owner sets the pass mark and duration")
    void ownerSetsSettings() {
        SopDocumentDto dto = sopService.updateSettings(1L, settings(80, 90), owner);

        assertEquals(80, dto.getPassMark());
        assertEquals(90, dto.getDurationMinutes());
        assertTrue(dto.isDurationSet());
        assertEquals(80, course.getPassMark());
        assertEquals(90, course.getDurationMinutes());
    }

    @Test
    @DisplayName("Settings outside the limits are refused with a reason, and nothing is saved")
    void settingsValidated() {
        for (SopCourseSettingsRequest bad : List.of(settings(0, 60), settings(101, 60), settings(null, 60),
                settings(70, 29), settings(70, 24 * 60 + 1), settings(70, null))) {
            assertThrows(BadRequestException.class, () -> sopService.updateSettings(1L, bad, owner));
        }
        assertThrows(BadRequestException.class, () -> sopService.updateSettings(1L, null, owner));
        assertDoesNotThrow(() -> sopService.updateSettings(1L, settings(1, 30), owner), "30 minutes is the minimum");
        assertDoesNotThrow(() -> sopService.updateSettings(1L, settings(100, 24 * 60), owner), "24 hours is the maximum");
    }

    @Test
    @DisplayName("Only the owner may change the settings")
    void ownerOnly() {
        SopViewer hr = new SopViewer(aisyah.getId(), true);
        SopViewer otherTrainer = new SopViewer(aisyah.getId(), false);

        assertThrows(ForbiddenRequestException.class, () -> sopService.updateSettings(1L, settings(90, 120), hr));
        assertThrows(ForbiddenRequestException.class, () -> sopService.updateSettings(1L, settings(90, 120), otherTrainer));
        assertEquals(70, course.getPassMark());
        assertNull(course.getDurationMinutes());
    }

    @Test
    @DisplayName("An assignment starts from the course's pass mark, may raise it, and may not lower it")
    void assignmentPassMark() {
        sopService.updateSettings(1L, settings(80, 90), owner);

        assignments.assign(request(null, aisyah), trainer.getId(), false);
        assertEquals(80, store.enrollmentOf(1L, aisyah.getId()).getPassMark(), "no pass mark given: the course's own");

        assignments.assign(request(95, aisyah), trainer.getId(), false);
        assertEquals(95, store.enrollmentOf(1L, aisyah.getId()).getPassMark(), "higher is allowed");

        BadRequestException lower = assertThrows(BadRequestException.class,
                () -> assignments.assign(request(70, aisyah), trainer.getId(), false));
        assertEquals("The pass mark can't be lower than the course's own (80%).", lower.getMessage());
        assertEquals(95, store.enrollmentOf(1L, aisyah.getId()).getPassMark());
    }

    @Test
    @DisplayName("Each enrolment keeps the duration that applied when assigned; later changes do not alter it")
    void durationCopiedToEnrolments() {
        assignments.assign(request(null, aisyah), trainer.getId(), false);
        assertEquals(60, store.enrollmentOf(1L, aisyah.getId()).getDurationMinutes(), "the default while not set");

        sopService.updateSettings(1L, settings(70, 90), owner);
        assertEquals(60, store.enrollmentOf(1L, aisyah.getId()).getDurationMinutes(), "existing enrolment unchanged");

        assignments.assign(request(null, aisyah), trainer.getId(), false);
        assertEquals(90, store.enrollmentOf(1L, aisyah.getId()).getDurationMinutes(), "assigning again applies the new terms");
    }

    @Test
    @DisplayName("Assign Training shows each course's pass mark and duration")
    void assignPageShowsSettings() {
        var listed = assignments.listCourses(trainer.getId(), false).get(0);
        assertEquals(70, listed.getPassMark());
        assertEquals(60, listed.getDurationMinutes());
        assertFalse(listed.isDurationSet());

        sopService.updateSettings(1L, settings(85, 45), owner);
        listed = assignments.listCourses(trainer.getId(), false).get(0);
        assertEquals(85, listed.getPassMark());
        assertEquals(45, listed.getDurationMinutes());
        assertTrue(listed.isDurationSet());
    }

    @Test
    @DisplayName("Joining an Open course uses its own pass mark and duration")
    void openCourseEnrolment() {
        course.setVisibility(SopVisibility.OPEN);
        sopService.updateSettings(1L, settings(85, 45), owner);

        assertEquals(85, learning.home(aisyah.getId()).getOpenCourses().get(0).getPassMark());
        SopLearningCourseDto joined = learning.enrol(1L, aisyah.getId());

        assertEquals(85, joined.getPassMark());
        assertEquals(45, store.enrollmentOf(1L, aisyah.getId()).getDurationMinutes());
    }

    private static SopCourseSettingsRequest settings(Integer passMark, Integer durationMinutes) {
        SopCourseSettingsRequest r = new SopCourseSettingsRequest();
        r.setPassMark(passMark);
        r.setDurationMinutes(durationMinutes);
        return r;
    }

    private SopAssignmentRequest request(Integer passMark, Staff person) {
        SopAssignmentRequest r = new SopAssignmentRequest();
        r.setSopDocumentId(1L);
        r.setMode(SopAssignmentMode.IMMEDIATE);
        r.setDeadline(store.today().plusDays(30));
        r.setDaysToComplete(14);
        r.setPassMark(passMark);
        r.setStaffIds(List.of(person.getId()));
        return r;
    }
}
