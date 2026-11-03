package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopCourseSettingsRequest;
import com.tbm.careerpathlearning.dto.SopDocumentDto;
import com.tbm.careerpathlearning.dto.SopLearningCourseDto;
import com.tbm.careerpathlearning.dto.SopLearningHomeDto;
import com.tbm.careerpathlearning.dto.SopModuleDto;
import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.dto.SopScoreboardDto;
import com.tbm.careerpathlearning.dto.TrainingAuditEntryDto;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopCourseStatus;
import com.tbm.careerpathlearning.enums.SopEnrollmentStatus;
import com.tbm.careerpathlearning.enums.SopVisibility;
import com.tbm.careerpathlearning.enums.TrainingAuditTarget;
import com.tbm.careerpathlearning.exception.ForbiddenRequestException;
import com.tbm.careerpathlearning.model.OrgChart;
import com.tbm.careerpathlearning.model.Role;
import com.tbm.careerpathlearning.model.SopAssignment;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopEnrollment;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.service.LocalSopGenerator;
import com.tbm.careerpathlearning.service.SopGenerationService;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Course versions: drafts, publishing and what happens to staff (FR-06-06, FR-07-13, FR-07-16 to FR-07-18). */
class SopVersionTest {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private SopTestStore store;
    private SopServiceImplTest.FakeGemini gemini;
    private SopServiceImpl courses;
    private SopLearningServiceImpl learning;
    private SopAssignmentServiceImpl assignments;
    private SopScoreboardServiceImpl scoreboard;
    private Staff trainer, hrPerson, aisyah, ravi, meiling;
    private SopViewer owner, hr;
    private SopModule first, second;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        gemini = new SopServiceImplTest.FakeGemini();
        SopGenerationService generation = store.wire(new SopGenerationService());
        ReflectionTestUtils.setField(generation, "geminiClient", gemini);
        ReflectionTestUtils.setField(generation, "localSopGenerator", new LocalSopGenerator());
        ReflectionTestUtils.setField(generation, "self", generation);
        courses = store.wire(new SopServiceImpl());
        ReflectionTestUtils.setField(courses, "sopGenerationService", generation);
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

        // A live Open course, ready to take: two modules, each with an approved 5-question quiz.
        SopDocument course = store.doc(1L, "Cash Handling", trainer.getId());
        course.setVisibility(SopVisibility.OPEN);
        course.setExtractedText("4.1 Handling Cash\nDo not leave the cash drawer unattended.");
        first = store.module(1L, 5);
        second = store.module(1L, 5);
    }

    /** Aisyah has not started, Ravi passed module 1, and Mei Ling completed the course, which was mandatory for her. */
    private void enrolThree() {
        store.enrollment(1L, aisyah.getId(), SopAssignmentMode.IMMEDIATE, today.minusDays(10), today.plusDays(2), 14, 70);
        SopEnrollment r = store.enrollment(1L, ravi.getId(), SopAssignmentMode.IMMEDIATE, today, today.plusDays(30), 14, 70);
        r.setStatus(SopEnrollmentStatus.IN_PROGRESS);
        r.setStartedAt(store.now().minusDays(1));
        store.attempt(r, first.getId(), 100, true, store.now().minusDays(1));
        SopEnrollment m = store.enrollment(1L, meiling.getId(), SopAssignmentMode.PORTAL, today.minusDays(30), today.plusDays(30), 21, 70);
        m.setMandatory(true);
        m.setStatus(SopEnrollmentStatus.COMPLETED);
        m.setCompletedAt(store.now().minusDays(2));
        m.setFinalScore(90);
    }

    @Test
    @DisplayName("Until anyone is assigned or joins, every change is made directly and there are no versions (P2-15)")
    void noVersionsBeforeAnyoneEnrols() {
        SopQuizQuestion q = store.questionsOf(first.getId()).get(0);

        courses.updateQuestion(q.getId(), question("Edited before anyone enrolled"), owner);
        courses.rejectModuleQuiz(second.getId(), "Ask about cash drops", owner);

        assertEquals("Edited before anyone enrolled", q.getQuestionText());
        assertTrue(store.questionsOf(second.getId()).stream().allMatch(x -> x.getReviewStatus() == ReviewStatus.REGENERATED));
        assertEquals(1, store.docs.size(), "no draft");
    }

    @Test
    @DisplayName("Material text and the course settings change on the live version directly, even with staff taking it (D2)")
    void materialAndSettingsStayLive() {
        enrolThree();

        courses.updateModule(first.getId(), "Handling cash safely", first.getContent(), owner);
        courses.updateSettings(1L, settings(80, 90), owner);

        assertEquals("Handling cash safely", first.getTitle());
        assertEquals(80, store.docs.get(1L).getPassMark());
        assertEquals(1, store.docs.size(), "no draft");
    }

    @Test
    @DisplayName("Once staff take a course, a quiz edit goes into a copy, a new draft version, and the live version stays as it was")
    void quizEditStartsADraft() {
        enrolThree();
        SopQuizQuestion live = store.questionsOf(first.getId()).get(0);
        String before = live.getQuestionText();

        SopQuizQuestionDto saved = courses.updateQuestion(live.getId(), question("When is the drawer counted?"), owner);

        SopDocument draft = onlyDraft();
        assertEquals(before, live.getQuestionText(), "the live question is untouched");
        assertNotEquals(live.getId(), saved.getId());
        assertEquals("When is the drawer counted?", store.questions.get(saved.getId()).getQuestionText());
        assertEquals(2, draft.getVersionNo());
        assertEquals(1L, draft.getCourseId());
        assertEquals(1L, draft.getPreviousVersionId());
        assertEquals(trainer.getId(), draft.getUploadedBy());
        assertEquals(SopVisibility.OPEN, draft.getVisibility());
        assertEquals("Cash Handling", draft.getTitle());

        // Every module and question was copied, in order, with its review status.
        List<SopModule> copies = modulesOf(draft);
        assertEquals(List.of(1, 2), copies.stream().map(SopModule::getModuleOrder).toList());
        for (int i = 0; i < 2; i++) {
            SopModule original = i == 0 ? first : second;
            assertEquals(original.getTitle(), copies.get(i).getTitle());
            assertEquals(original.getContent(), copies.get(i).getContent());
            assertEquals(ReviewStatus.APPROVED, copies.get(i).getReviewStatus());
            assertEquals(5, store.questionsOf(copies.get(i).getId()).size());
        }
        assertEquals(texts(second), texts(copies.get(1)));
        assertEquals(5, store.questionsOf(first.getId()).size(), "the live quiz keeps its questions");

        // The draft's quiz history starts with the copy, then the edit.
        assertEquals(List.of("EDITED", "COPIED"), courses.getQuizHistory(copies.get(0).getId(), owner).stream()
                .map(e -> e.getAction().name()).toList());
        // The live version points at its draft and can't change meanwhile; staff carry on with it.
        SopDocumentDto liveDto = courses.getDetail(1L, owner).getDocument();
        assertEquals(draft.getId(), liveDto.getDraftId());
        assertFalse(liveDto.isEditable());
        assertTrue(store.enrollments.values().stream().allMatch(e -> e.getSopDocumentId().equals(1L)));
        TrainingAuditEntryDto logged = history().get(0);
        assertEquals("COURSE_DRAFT_CREATED", logged.getAction());
        assertEquals("Draft v2 started from v1, because a question in module 1's quiz was edited.", logged.getDetail());
    }

    @Test
    @DisplayName("A course has one draft at a time: the live version can't change while it exists, and further changes go into it")
    void oneDraftAtATime() {
        enrolThree();
        SopQuizQuestion liveQuestion = store.questionsOf(first.getId()).get(0);
        SopDocument draft = draftWithAnEdit();

        assertRefused("Draft v2 of this course is in progress", () -> courses.updateQuestion(liveQuestion.getId(), question("Again"), owner));
        assertRefused("Draft v2 of this course is in progress", () -> courses.updateModule(first.getId(), "New title", "{}", owner));
        assertRefused("Draft v2 of this course is in progress", () -> courses.updateSettings(1L, settings(80, 90), owner));
        assertRefused("Draft v2 of this course is in progress", () -> courses.deleteDocument(1L, owner));
        assertRefused("Publish or discard it before archiving the course", () -> courses.archiveDocument(1L, owner));

        SopQuizQuestion inDraft = store.questionsOf(modulesOf(draft).get(0).getId()).get(1);
        courses.updateQuestion(inDraft.getId(), question("Edited in the draft"), owner);

        assertEquals("Edited in the draft", inDraft.getQuestionText());
        assertEquals(2, store.docs.size(), "still the live version and one draft");
        assertTrue(store.docs.get(1L).isLive());
    }

    @Test
    @DisplayName("Rejecting approved material on a course staff are taking regenerates it in a new draft (FR-07-13)")
    void materialRejectionGoesIntoADraft() {
        enrolThree();
        String liveTitle = first.getTitle();

        SopModuleDto regenerated = courses.rejectModule(first.getId(), "Explain the cash log", owner);

        SopDocument draft = onlyDraft();
        assertEquals(draft.getId(), regenerated.getSopDocumentId());
        assertEquals(1, regenerated.getModuleOrder());
        assertEquals(ReviewStatus.REGENERATED, regenerated.getReviewStatus());
        assertEquals(liveTitle, first.getTitle(), "the live material is untouched");
        assertEquals(ReviewStatus.APPROVED, first.getReviewStatus());
    }

    @Test
    @DisplayName("Rejecting an approved quiz on a course staff are taking regenerates it in a new draft; the live quiz stays (FR-08-10)")
    void quizRejectionGoesIntoADraft() {
        enrolThree();
        List<String> liveQuiz = texts(second);

        SopModuleDto regenerated = courses.rejectModuleQuiz(second.getId(), "Ask about cash drops", owner);

        assertEquals(onlyDraft().getId(), regenerated.getSopDocumentId());
        assertEquals(2, regenerated.getModuleOrder());
        assertTrue(regenerated.getQuiz().stream().allMatch(q -> q.getReviewStatus() == ReviewStatus.REGENERATED));
        assertEquals(liveQuiz, texts(second), "the live quiz is untouched");
        assertTrue(store.questionsOf(second.getId()).stream().allMatch(q -> q.getReviewStatus() == ReviewStatus.APPROVED));
    }

    @Test
    @DisplayName("When the AI fails, the draft made for the rejection is removed again and nothing changes")
    void failedRegenerationLeavesNoDraft() {
        enrolThree();
        gemini.down = true;

        assertThrows(RuntimeException.class, () -> courses.rejectModuleQuiz(second.getId(), "Ask about cash drops", owner));

        assertEquals(Set.of(1L), store.docs.keySet(), "no draft left behind");
        assertEquals(10, store.questions.size(), "no copied questions left behind");
        assertTrue(store.events.isEmpty(), "no copied history left behind");
        assertTrue(courses.getDetail(1L, owner).getDocument().isEditable());
        assertEquals(List.of("COURSE_DRAFT_DISCARDED", "COURSE_DRAFT_CREATED"),
                history().stream().map(TrainingAuditEntryDto::getAction).toList());
    }

    @Test
    @DisplayName("A draft can be published only once every module's material and quiz is approved (P2-19)")
    void publishNeedsEverythingApproved() {
        enrolThree();
        courses.addQuestion(first.getId(), question("A new question"), owner); // goes into a new draft, waiting for review
        SopDocument draft = onlyDraft();

        assertRefused("Approve every module's material and quiz", () -> courses.publishDraft(draft.getId(), false, owner));
        assertTrue(store.docs.get(1L).isLive());
        assertEquals(6, store.questionsOf(modulesOf(draft).get(0).getId()).size());

        courses.approveModuleQuiz(modulesOf(draft).get(0).getId(), owner);
        assertDoesNotThrow(() -> courses.publishDraft(draft.getId(), false, owner));
    }

    @Test
    @DisplayName("Publishing moves staff still taking the course to the new version with a fresh window, keeps completed records, and tells everyone")
    void publishMovesStaff() {
        enrolThree();
        SopDocument draft = draftWithAnEdit();
        store.clock.advance(Duration.ofDays(1));
        LocalDate goLive = store.today();
        store.notifications.clear();

        SopDocumentDto published = courses.publishDraft(draft.getId(), false, owner);

        assertEquals(SopCourseStatus.LIVE, published.getCourseStatus());
        assertEquals(2, published.getVersionNo());
        assertTrue(published.isEditable());
        assertEquals(SopCourseStatus.SUPERSEDED, store.docs.get(1L).getCourseStatus());

        // Decision E: Aisyah restarts on v2 on her old terms, with a fresh window from go-live that her old deadline can't cut.
        SopEnrollment aisyahNew = store.enrollmentOf(draft.getId(), aisyah.getId());
        assertEquals(SopEnrollmentStatus.WITHDRAWN, store.enrollmentOf(1L, aisyah.getId()).getStatus());
        assertEquals(SopEnrollmentStatus.NOT_STARTED, aisyahNew.getStatus());
        assertEquals(SopAssignmentMode.IMMEDIATE, aisyahNew.getMode());
        assertEquals(goLive, aisyahNew.getEffectiveDate());
        assertEquals(goLive.plusDays(14), aisyahNew.dueDate());
        assertEquals(1L, aisyahNew.getAssignmentId());
        assertEquals(SopEnrollmentStatus.WITHDRAWN, store.enrollmentOf(1L, ravi.getId()).getStatus());
        assertEquals(SopEnrollmentStatus.NOT_STARTED, store.enrollmentOf(draft.getId(), ravi.getId()).getStatus());
        assertEquals(1, store.attempts.size(), "Ravi's progress on v1 stays in his history");
        assertEquals(SopEnrollmentStatus.COMPLETED, store.enrollmentOf(1L, meiling.getId()).getStatus());
        assertTrue(store.enrollments.values().stream()
                .noneMatch(e -> e.getSopDocumentId().equals(draft.getId()) && e.getStaffId().equals(meiling.getId())));

        Map<UUID, SopTestStore.Sent> sent = store.notifications.stream()
                .collect(Collectors.toMap(SopTestStore.Sent::recipient, n -> n));
        assertEquals(Set.of(aisyah.getId(), ravi.getId(), meiling.getId()), sent.keySet(), "everyone enrolled is told");
        SopTestStore.Sent moved = sent.get(aisyah.getId());
        assertEquals("New version of Cash Handling", moved.title());
        assertEquals("Version 2 of \"Cash Handling\" has replaced version 1, which you were taking, so you start again on it. "
                + "Your earlier progress is kept in your history. Complete it by " + DATE.format(goLive.plusDays(14)) + ".", moved.body());
        assertEquals("/my-learning/course/" + draft.getId(), moved.link());
        SopTestStore.Sent kept = sent.get(meiling.getId());
        assertEquals("Version 2 of \"Cash Handling\" is live. You don't need to take it again: your certificate for "
                + "version 1 stays valid.", kept.body());
        assertEquals("/my-learning/course/1", kept.link());

        TrainingAuditEntryDto logged = history().get(0);
        assertEquals("COURSE_VERSION_PUBLISHED", logged.getAction());
        assertEquals("v2 is live, replacing v1. Staff moved to it: 2. Completed staff keeping their certificate: 1.",
                logged.getDetail());
    }

    @Test
    @DisplayName("When completed staff must redo it, each gets their original days from go-live, stays mandatory, and keeps the old certificate")
    void redoCompleted() {
        enrolThree();
        SopDocument draft = draftWithAnEdit();
        store.notifications.clear();

        courses.publishDraft(draft.getId(), true, owner);

        SopEnrollment redo = store.enrollmentOf(draft.getId(), meiling.getId());
        assertEquals(SopEnrollmentStatus.NOT_STARTED, redo.getStatus());
        assertEquals(SopAssignmentMode.IMMEDIATE, redo.getMode());
        assertEquals(today, redo.getEffectiveDate());
        assertEquals(today.plusDays(21), redo.dueDate());
        assertTrue(redo.isMandatory());
        assertEquals(SopEnrollmentStatus.COMPLETED, store.enrollmentOf(1L, meiling.getId()).getStatus());
        assertNotNull(learning.course(1L, meiling.getId()).getCertificate(), "her certificate for v1 stays");
        SopTestStore.Sent told = store.notifications.stream().filter(n -> n.recipient().equals(meiling.getId()))
                .findFirst().orElseThrow();
        assertEquals("Please complete the new version: Cash Handling", told.title());
        assertEquals("Version 2 of \"Cash Handling\" is live, and everyone who completed an earlier version must complete "
                + "it too. Complete it by " + DATE.format(today.plusDays(21)) + ". You keep your certificate for version 1.",
                told.body());
        assertTrue(history().get(0).getDetail().endsWith("Completed staff asked to complete it too: 1."));
    }

    @Test
    @DisplayName("After publishing, assigning, joining and the review lists use the new version, and the old version's records stay")
    void afterPublishing() {
        enrolThree();
        SopAssignment assignment = new SopAssignment();
        assignment.setId(1L);
        assignment.setSopDocumentId(1L);
        assignment.setAssignedBy(trainer.getId());
        assignment.setAudience("Sales Department");
        assignment.setMode(SopAssignmentMode.IMMEDIATE);
        assignment.setEffectiveDate(today);
        assignment.setDeadline(today.plusDays(30));
        assignment.setDaysToComplete(14);
        assignment.setPassMark(70);
        assignment.setStaffCount(3);
        assignment.setCreatedAt(store.now());
        store.assignments.put(1L, assignment);
        SopDocument draft = draftWithAnEdit();
        courses.publishDraft(draft.getId(), false, owner);
        Staff newcomer = store.person("Lim Wei", null);

        assertEquals(List.of(draft.getId()), assignments.listCourses(trainer.getId(), false).stream().map(c -> c.getId()).toList());
        assertEquals(1, assignments.history(draft.getId(), trainer.getId(), false).size(), "the assignment made on v1 still shows");
        assertEquals(List.of(draft.getId()), openCourseIds(learning.home(newcomer.getId())));
        assertTrue(openCourseIds(learning.home(meiling.getId())).isEmpty(), "she completed v1, so v2 isn't offered to her");
        assertEquals(List.of(draft.getId()), ids(courses.listDocuments(owner, true, false)));
        assertEquals(List.of(draft.getId()), ids(courses.listDocuments(hr, false, false)));

        SopScoreboardDto board = scoreboard.scoreboard(trainer.getId(), false);
        assertEquals(5, board.getRecords().size(), "v1's three records and v2's two");
        assertTrue(board.getCourses().stream().filter(c -> c.getVersionNo() == 1).allMatch(SopScoreboardDto.Course::isSuperseded));
        assertEquals(Set.of(1, 2), board.getCourses().stream().map(SopScoreboardDto.Course::getVersionNo).collect(Collectors.toSet()));

        SopLearningCourseDto oldCard = learning.home(ravi.getId()).getCourses().stream()
                .filter(c -> c.getSopDocumentId().equals(1L)).findFirst().orElseThrow();
        assertTrue(oldCard.isCourseSuperseded());
        assertFalse(oldCard.isAvailable());
        assertRefused("withdrawn", () -> learning.start(1L, ravi.getId()));
        assertEquals(2, learning.course(draft.getId(), ravi.getId()).getCourse().getVersionNo());
        assertEquals(1, learning.course(1L, meiling.getId()).getCertificate().getVersionNo(), "certificates show their version");
    }

    @Test
    @DisplayName("Discarding a draft removes it and everything copied for it, and the live version can change again")
    void discardDraft() {
        enrolThree();
        SopDocument draft = draftWithAnEdit();

        courses.deleteDocument(draft.getId(), owner);

        assertEquals(Set.of(1L), store.docs.keySet());
        assertTrue(store.modules.values().stream().allMatch(m -> m.getSopDocumentId().equals(1L)));
        assertEquals(10, store.questions.size());
        assertTrue(store.events.isEmpty());
        assertTrue(courses.getDetail(1L, owner).getDocument().isEditable());
        assertEquals("COURSE_DRAFT_DISCARDED", history().get(0).getAction());
    }

    @Test
    @DisplayName("Only the owner sees and opens a draft, and only they can publish it; HR keeps seeing the live version")
    void draftsAreTheOwners() {
        enrolThree();
        SopDocument draft = draftWithAnEdit();

        assertEquals(List.of(1L), ids(courses.listDocuments(hr, false, false)));
        assertEquals(Set.of(1L, draft.getId()), Set.copyOf(ids(courses.listDocuments(owner, true, false))));
        assertRefused("Only the course's owner can open its draft", () -> courses.getDetail(draft.getId(), hr));
        assertThrows(ForbiddenRequestException.class, () -> courses.publishDraft(draft.getId(), false, hr));
    }

    @Test
    @DisplayName("A revised SOP uploaded as a new version becomes the course's next draft, with the course's settings (FR-06-06)")
    void revisedSopStartsTheNextDraft() {
        SopDocument live = store.docs.get(1L);
        live.setPassMark(80);
        live.setDurationMinutes(90);

        SopDocument shell = store.versions().newDraftOf(live);

        assertEquals(SopCourseStatus.DRAFT, shell.getCourseStatus());
        assertEquals(2, shell.getVersionNo());
        assertEquals(1L, shell.getCourseId());
        assertEquals(1L, shell.getPreviousVersionId());
        assertEquals(80, shell.getPassMark());
        assertEquals(90, shell.getDurationMinutes());
        assertEquals(SopVisibility.OPEN, shell.getVisibility());
        assertEquals(trainer.getId(), shell.getUploadedBy());
        store.repository(SopDocumentRepository.class).save(shell);
        assertRefused("Draft v2 of this course is in progress", () -> store.versions().newDraftOf(live));
    }

    @Test
    @DisplayName("A course staff are taking can't be generated again from scratch; a revised SOP becomes a new version instead")
    void noFullRegenerationWithLearners() {
        enrolThree();

        assertRefused("Upload the revised SOP as a new version instead", () -> courses.regenerateDocument(1L, owner));
    }

    @Test
    @DisplayName("Version 3 follows version 2: a second draft starts from the live v2, and the history covers every version")
    void versionsCount() {
        enrolThree();
        SopDocument v2 = draftWithAnEdit();
        courses.publishDraft(v2.getId(), false, owner);
        SopQuizQuestion inV2 = store.questionsOf(modulesOf(v2).get(0).getId()).get(0);

        courses.updateQuestion(inV2.getId(), question("Edited in v3"), owner);

        SopDocument v3 = onlyDraft();
        assertEquals(3, v3.getVersionNo());
        assertEquals(v2.getId(), v3.getPreviousVersionId());
        assertEquals(1L, v3.getCourseId());
        assertEquals(List.of("COURSE_DRAFT_CREATED", "COURSE_VERSION_PUBLISHED", "COURSE_DRAFT_CREATED"),
                history().stream().map(TrainingAuditEntryDto::getAction).toList());
    }

    // === helpers ============================================================

    /** Edits a question of the live version, which starts draft v2. */
    private SopDocument draftWithAnEdit() {
        SopQuizQuestion q = store.questionsOf(first.getId()).get(0);
        courses.updateQuestion(q.getId(), question("When is the drawer counted?"), owner);
        return onlyDraft();
    }

    private SopDocument onlyDraft() {
        List<SopDocument> drafts = store.docs.values().stream().filter(SopDocument::isDraft).toList();
        assertEquals(1, drafts.size(), "one draft");
        return drafts.get(0);
    }

    private List<SopModule> modulesOf(SopDocument doc) {
        return store.modules.values().stream().filter(m -> m.getSopDocumentId().equals(doc.getId()))
                .sorted((a, b) -> Integer.compare(a.getModuleOrder(), b.getModuleOrder())).toList();
    }

    private List<String> texts(SopModule module) {
        return store.questionsOf(module.getId()).stream().map(SopQuizQuestion::getQuestionText).toList();
    }

    /** The course's training audit history, newest first; every version shares it. */
    private List<TrainingAuditEntryDto> history() {
        store.clock.advance(Duration.ofSeconds(1)); // keeps later entries after earlier ones
        return store.audit().history(TrainingAuditTarget.COURSE, "1");
    }

    private static SopQuizQuestionDto question(String text) {
        SopQuizQuestionDto dto = new SopQuizQuestionDto();
        dto.setQuestionType(QuizQuestionType.MULTIPLE_CHOICE);
        dto.setQuestionText(text);
        dto.setOptions(List.of("A0", "B0", "C0", "D0"));
        dto.setCorrectAnswer("B0");
        dto.setExplanation("Because B0");
        return dto;
    }

    private static SopCourseSettingsRequest settings(int passMark, int minutes) {
        SopCourseSettingsRequest r = new SopCourseSettingsRequest();
        r.setPassMark(passMark);
        r.setDurationMinutes(minutes);
        return r;
    }

    private static List<Long> ids(List<SopDocumentDto> docs) {
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
