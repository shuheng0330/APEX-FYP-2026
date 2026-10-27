package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopAssignmentPreviewDto;
import com.tbm.careerpathlearning.dto.SopAssignmentRequest;
import com.tbm.careerpathlearning.dto.SopAssignmentResultDto;
import com.tbm.careerpathlearning.dto.SopCourseSettingsRequest;
import com.tbm.careerpathlearning.dto.SopDocumentDto;
import com.tbm.careerpathlearning.dto.SopLearningCourseDetailDto;
import com.tbm.careerpathlearning.dto.SopLearningCourseDto;
import com.tbm.careerpathlearning.dto.SopLearningHomeDto;
import com.tbm.careerpathlearning.dto.SopScoreboardDto;
import com.tbm.careerpathlearning.dto.SopScoreboardRecordDto;
import com.tbm.careerpathlearning.dto.TrainingAuditEntryDto;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopCourseStatus;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.SopVisibility;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Delete only while nobody has enrolled, otherwise archive; and restore (FR-06-14 to FR-06-16). */
class SopArchiveTest {

    private SopTestStore store;
    private SopServiceImpl courses;
    private SopLearningServiceImpl learning;
    private SopAssignmentServiceImpl assignments;
    private SopScoreboardServiceImpl scoreboard;
    private Staff trainer, hrPerson, aisyah, ravi, meiling;
    private SopViewer owner, hr, otherTrainer;
    private SopModule first;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        courses = store.wire(new SopServiceImpl());
        learning = store.wire(new SopLearningServiceImpl());
        assignments = store.wire(new SopAssignmentServiceImpl());
        scoreboard = store.wire(new SopScoreboardServiceImpl());
        today = store.today();
        OrgChart sales = store.department(10L, "Sales Department");
        Role salesExecutive = store.role(20L, "Sales Executive", sales);
        trainer = store.person("Tan Wei Ming", null);
        hrPerson = store.person("Nadia Rahman", null);
        aisyah = store.person("Nur Aisyah", salesExecutive);
        ravi = store.person("Ravi Kumar", salesExecutive);
        meiling = store.person("Chong Mei Ling", salesExecutive);
        owner = new SopViewer(trainer.getId(), false);
        hr = new SopViewer(hrPerson.getId(), true);
        otherTrainer = new SopViewer(UUID.randomUUID(), false);

        // An Open course, ready to take: two modules, each with an approved 5-question quiz.
        store.doc(1L, "Cash Handling", trainer.getId()).setVisibility(SopVisibility.OPEN);
        first = store.module(1L, 5);
        store.module(1L, 5);
    }

    /** Aisyah has not started and is past due, Ravi passed the first module, and Mei Ling completed the course. */
    private void enrolThree() {
        store.enrollment(1L, aisyah.getId(), SopAssignmentMode.IMMEDIATE, today.minusDays(20), today.minusDays(5), 14, 70);
        SopEnrollment r = store.enrollment(1L, ravi.getId(), SopAssignmentMode.IMMEDIATE, today, today.plusDays(30), 14, 70);
        r.setStatus(SopEnrollmentStatus.IN_PROGRESS);
        r.setStartedAt(store.now().minusDays(1));
        store.attempt(r, first.getId(), 100, true, store.now().minusDays(1));
        SopEnrollment m = store.enrollment(1L, meiling.getId(), SopAssignmentMode.IMMEDIATE, today, today.plusDays(30), 14, 70);
        m.setStatus(SopEnrollmentStatus.COMPLETED);
        m.setCompletedAt(store.now().minusDays(2));
        m.setFinalScore(90);
    }

    @Test
    @DisplayName("A course nobody has enrolled in can be deleted; once anyone has, Delete is refused and points to Archive")
    void deleteOnlyWhileNobodyEnrolled() {
        store.doc(2L, "Draft Notes", trainer.getId());
        store.module(2L, 4);

        courses.deleteDocument(2L, owner);

        assertFalse(store.docs.containsKey(2L));
        store.enrollment(1L, aisyah.getId(), SopAssignmentMode.PORTAL, today, today.plusDays(30), 14, 70)
                .setStatus(SopEnrollmentStatus.WITHDRAWN);
        assertRefused("Archive it instead", () -> courses.deleteDocument(1L, owner));
        assertTrue(store.docs.containsKey(1L));
        assertEquals(1, store.enrollments.size(), "even a withdrawn enrolment keeps the course");
    }

    @Test
    @DisplayName("Archiving withdraws and tells the staff still taking the course, and keeps completed records and every attempt")
    void archiveWithdrawsStaffStillTakingIt() {
        enrolThree();
        SopDocumentDto before = courses.listDocuments(owner, true, false).get(0);
        assertEquals(3, before.getEnrolledCount());
        assertEquals(2, before.getActiveEnrollmentCount());

        SopDocumentDto dto = courses.archiveDocument(1L, owner);

        assertEquals(SopCourseStatus.ARCHIVED, dto.getCourseStatus());
        assertEquals(store.now(), dto.getArchivedAt());
        assertFalse(dto.isEditable());
        assertEquals(3, dto.getEnrolledCount());
        assertEquals(0, dto.getActiveEnrollmentCount());
        assertEquals(SopEnrollmentStatus.WITHDRAWN, store.enrollmentOf(1L, aisyah.getId()).getStatus());
        assertEquals(SopEnrollmentStatus.WITHDRAWN, store.enrollmentOf(1L, ravi.getId()).getStatus());
        assertEquals(SopEnrollmentStatus.COMPLETED, store.enrollmentOf(1L, meiling.getId()).getStatus());
        assertEquals(1, store.attempts.size(), "Ravi's progress is kept");

        assertEquals(List.of(aisyah.getId(), ravi.getId()),
                store.notifications.stream().map(SopTestStore.Sent::recipient).toList(), "only those withdrawn are told");
        SopTestStore.Sent sent = store.notifications.get(0);
        assertEquals(NotificationType.COURSE_WITHDRAWN, sent.type());
        assertEquals("Course withdrawn: Cash Handling", sent.title());
        assertEquals("\"Cash Handling\" has been archived, so you have been withdrawn from it. "
                + "Your progress so far is kept in My Learning.", sent.body());
        assertEquals("/my-learning/course/1", sent.link());

        TrainingAuditEntryDto logged = store.audit().history(TrainingAuditTarget.COURSE, "1").get(0);
        assertEquals("COURSE_ARCHIVED", logged.getAction());
        assertEquals("Tan Wei Ming", logged.getActorName());
        assertEquals("Staff withdrawn: 2. Completed records kept: 1.", logged.getDetail());
    }

    @Test
    @DisplayName("Only the owner can archive, and only a live course")
    void archiveIsForTheOwner() {
        enrolThree();

        ForbiddenRequestException byHr = assertThrows(ForbiddenRequestException.class, () -> courses.archiveDocument(1L, hr));
        assertTrue(byHr.getMessage().contains("Only its owner can"), byHr.getMessage());
        assertThrows(ForbiddenRequestException.class, () -> courses.archiveDocument(1L, otherTrainer));
        assertTrue(store.docs.get(1L).isLive());
        assertTrue(store.notifications.isEmpty());

        courses.archiveDocument(1L, owner);
        assertRefused("already archived", () -> courses.archiveDocument(1L, owner));
    }

    @Test
    @DisplayName("An archived course leaves the course lists and can't be changed, but its owner and HR can still open it")
    void archivedCourseIsReadOnly() {
        enrolThree();
        courses.archiveDocument(1L, owner);

        assertTrue(courses.listDocuments(owner, true, false).isEmpty());
        assertTrue(courses.listDocuments(hr, false, false).isEmpty());
        assertEquals(List.of(1L), idsOf(courses.listDocuments(owner, true, true)));
        assertEquals(List.of(1L), idsOf(courses.listDocuments(hr, false, true)));
        assertTrue(courses.listDocuments(otherTrainer, false, true).isEmpty());

        SopDocumentDto opened = courses.getDetail(1L, owner).getDocument();
        assertEquals(SopCourseStatus.ARCHIVED, opened.getCourseStatus());
        assertFalse(opened.isEditable());
        SopCourseSettingsRequest settings = new SopCourseSettingsRequest();
        settings.setPassMark(80);
        settings.setDurationMinutes(90);
        assertRefused("Restore it before changing it", () -> courses.updateSettings(1L, settings, owner));
        assertRefused("Restore it before changing it", () -> courses.approveModule(first.getId(), owner));
        assertRefused("Restore it before changing it", () -> courses.deleteDocument(1L, owner));

        TrainerAccessServiceImpl access = store.wire(new TrainerAccessServiceImpl());
        assertTrue(access.courseOwners().get(0).isArchived(), "the Course owners tab marks it archived");
    }

    @Test
    @DisplayName("The owner or HR can restore an archived course; it is Live again and withdrawn staff stay withdrawn")
    void restore() {
        enrolThree();
        courses.archiveDocument(1L, owner);
        store.clock.advance(Duration.ofHours(1));
        assertThrows(ForbiddenRequestException.class, () -> courses.restoreDocument(1L, otherTrainer));

        SopDocumentDto dto = courses.restoreDocument(1L, hr);

        assertEquals(SopCourseStatus.LIVE, dto.getCourseStatus());
        assertNull(dto.getArchivedAt());
        assertFalse(dto.isEditable(), "HR still only reads it");
        assertTrue(courses.getDetail(1L, owner).getDocument().isEditable(), "its owner can change it again");
        assertEquals(SopEnrollmentStatus.WITHDRAWN, store.enrollmentOf(1L, ravi.getId()).getStatus(), "not re-enrolled (D3)");
        List<TrainingAuditEntryDto> history = store.audit().history(TrainingAuditTarget.COURSE, "1");
        assertEquals(List.of("COURSE_RESTORED", "COURSE_ARCHIVED"), history.stream().map(TrainingAuditEntryDto::getAction).toList());
        assertEquals("Nadia Rahman", history.get(0).getActorName());
        assertRefused("not archived", () -> courses.restoreDocument(1L, owner));

        courses.archiveDocument(1L, owner);
        assertDoesNotThrow(() -> courses.restoreDocument(1L, owner), "the owner can restore too");
        assertEquals(2, store.notifications.size(), "archiving again withdraws nobody new");
    }

    @Test
    @DisplayName("Learners can't join or continue an archived course; it shows as Withdrawn or Archived, and certificates are kept")
    void learnersAfterArchiving() {
        enrolThree();
        Staff newcomer = store.person("Lim Wei", null);
        assertEquals(List.of(1L), openCourseIds(learning.home(newcomer.getId())), "open before archiving");

        courses.archiveDocument(1L, owner);

        assertTrue(learning.home(newcomer.getId()).getOpenCourses().isEmpty());
        assertRefused("not open for enrolment", () -> learning.enrol(1L, newcomer.getId()));

        SopLearningCourseDto withdrawn = learning.home(aisyah.getId()).getCourses().get(0);
        assertEquals(SopEnrollmentStatus.WITHDRAWN, withdrawn.getStatus());
        assertFalse(withdrawn.isOverdue(), "past due, but withdrawn");
        assertFalse(withdrawn.isAvailable());
        assertTrue(withdrawn.isCourseArchived());
        assertRefused("withdrawn", () -> learning.start(1L, ravi.getId()));
        assertRefused("withdrawn", () -> learning.material(first.getId(), ravi.getId()));
        assertEquals(1, learning.course(1L, ravi.getId()).getCourse().getModulesPassed(), "his progress is kept");

        SopLearningCourseDetailDto done = learning.course(1L, meiling.getId());
        assertNotNull(done.getCertificate(), "the certificate is kept");
        assertTrue(done.getCourse().isCourseArchived());
        assertFalse(done.getCourse().isAvailable());
        assertRefused("archived", () -> learning.material(first.getId(), meiling.getId()));
    }

    @Test
    @DisplayName("After a restore, someone withdrawn can join the Open course again: a fresh start, with passed quizzes still passed")
    void joinAgainAfterRestore() {
        enrolThree();
        courses.archiveDocument(1L, owner);
        courses.restoreDocument(1L, owner);

        assertEquals(List.of(1L), openCourseIds(learning.home(ravi.getId())));
        assertTrue(openCourseIds(learning.home(meiling.getId())).isEmpty(), "she completed it");

        SopLearningCourseDto joined = learning.enrol(1L, ravi.getId());

        SopEnrollment e = store.enrollmentOf(1L, ravi.getId());
        assertEquals(SopEnrollmentStatus.NOT_STARTED, e.getStatus());
        assertNull(e.getStartedAt(), "a fresh window once he starts");
        assertNull(e.getAssignmentId(), "now self-enrolled");
        assertFalse(e.isMandatory());
        assertEquals(1, joined.getModulesPassed());
        assertEquals(1, store.enrollments.values().stream().filter(x -> x.getStaffId().equals(ravi.getId())).count(),
                "still one enrolment per person");
    }

    @Test
    @DisplayName("Assign Training leaves archived courses out, and the API refuses to assign them or change who can join")
    void archivedCourseCannotBeAssigned() {
        courses.archiveDocument(1L, owner);

        assertTrue(assignments.listCourses(trainer.getId(), false).isEmpty());
        assertTrue(assignments.listCourses(hrPerson.getId(), true).isEmpty());
        assertRefused("archived", () -> assignments.assign(assignTo(aisyah), trainer.getId(), false));
        assertRefused("archived", () -> assignments.preview(assignTo(aisyah)));
        assertRefused("archived", () -> assignments.setVisibility(1L, SopVisibility.PRIVATE, trainer.getId(), false));
        assertTrue(store.enrollments.isEmpty());
    }

    @Test
    @DisplayName("Assigning a restored course again gives withdrawn staff a fresh start on the new terms, as a new assignment")
    void assignAgainAfterRestore() {
        enrolThree();
        courses.archiveDocument(1L, owner);
        courses.restoreDocument(1L, owner);
        store.notifications.clear();
        SopAssignmentRequest request = assignTo(ravi, meiling);

        assertEquals(Map.of("Ravi Kumar", "NEW", "Chong Mei Ling", "COMPLETED"), assignments.preview(request).getStaff().stream()
                .collect(Collectors.toMap(SopAssignmentPreviewDto.Row::getName, SopAssignmentPreviewDto.Row::getOutcome)));
        SopAssignmentResultDto result = assignments.assign(request, trainer.getId(), false);

        assertEquals(1, result.getAssigned());
        assertEquals(0, result.getUpdated());
        assertEquals(1, result.getSkippedCompleted());
        SopEnrollment e = store.enrollmentOf(1L, ravi.getId());
        assertEquals(SopEnrollmentStatus.NOT_STARTED, e.getStatus());
        assertNull(e.getStartedAt());
        assertEquals(today.plusDays(30), e.getDeadline());
        assertEquals(List.of(NotificationType.COURSE_ASSIGNED), store.notifications.stream().map(SopTestStore.Sent::type).toList());
        assertEquals(SopEnrollmentStatus.WITHDRAWN, store.enrollmentOf(1L, aisyah.getId()).getStatus(), "only those assigned again");
    }

    @Test
    @DisplayName("The Scoreboard keeps an archived course's records, marked Archived, with unfinished staff shown as Withdrawn")
    void scoreboardAfterArchiving() {
        enrolThree();
        courses.archiveDocument(1L, owner);

        SopScoreboardDto board = scoreboard.scoreboard(trainer.getId(), false);

        assertTrue(board.getCourses().get(0).isArchived());
        assertEquals(Map.of("Nur Aisyah", "WITHDRAWN", "Ravi Kumar", "WITHDRAWN", "Chong Mei Ling", "COMPLETED"),
                board.getRecords().stream().collect(Collectors.toMap(SopScoreboardRecordDto::getStaffName,
                        SopScoreboardRecordDto::getStatus)));
        assertTrue(board.getRecords().stream().allMatch(SopScoreboardRecordDto::isCourseArchived));
        assertTrue(board.getRecords().stream().noneMatch(SopScoreboardRecordDto::isOverdue), "withdrawn staff are never overdue");
        assertEquals(90, board.getRecords().stream().filter(r -> r.getStaffName().equals("Chong Mei Ling"))
                .findFirst().orElseThrow().getFinalScore());
    }

    @Test
    @DisplayName("Before archiving, nothing changes: the course is Live, listed, and assignable as before")
    void liveCourseIsUnchanged() {
        SopDocumentDto listed = courses.listDocuments(owner, true, false).get(0);

        assertEquals(SopCourseStatus.LIVE, listed.getCourseStatus());
        assertNull(listed.getArchivedAt());
        assertTrue(listed.isEditable());
        assertEquals(List.of(1L), assignments.listCourses(trainer.getId(), false).stream().map(c -> c.getId()).toList());
        assertTrue(courses.listDocuments(owner, true, true).isEmpty(), "nothing archived yet");
    }

    private SopAssignmentRequest assignTo(Staff... staff) {
        SopAssignmentRequest r = new SopAssignmentRequest();
        r.setSopDocumentId(1L);
        r.setStaffIds(Arrays.stream(staff).map(Staff::getId).toList());
        r.setMode(SopAssignmentMode.IMMEDIATE);
        r.setMandatory(true);
        r.setDeadline(today.plusDays(30));
        r.setDaysToComplete(14);
        return r;
    }

    private static List<Long> idsOf(List<SopDocumentDto> docs) {
        return docs.stream().map(SopDocumentDto::getId).toList();
    }

    private static List<Long> openCourseIds(SopLearningHomeDto home) {
        return home.getOpenCourses().stream().map(SopLearningCourseDto::getSopDocumentId).toList();
    }

    private static void assertRefused(String expected, Executable action) {
        RuntimeException e = assertThrows(RuntimeException.class, action);
        assertTrue(e.getMessage().contains(expected), e.getMessage());
    }
}
