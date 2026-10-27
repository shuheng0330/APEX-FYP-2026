package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopAssignableCourseDto;
import com.tbm.careerpathlearning.dto.SopAssignmentRequest;
import com.tbm.careerpathlearning.dto.SopModuleDto;
import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.dto.SopQuizResultDto;
import com.tbm.careerpathlearning.dto.SopQuizSettingsRequest;
import com.tbm.careerpathlearning.dto.SopQuizSubmission;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopVisibility;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quiz size: five questions to approve a quiz and to give its course to staff, how many questions an attempt presents,
 * and deleting questions (FR-08-11, FR-08-14, FR-08-15, FR-09-10; decisions A and B, 23 Oct).
 */
class SopQuizSizeTest {

    private SopTestStore store;
    private SopServiceImpl courses;
    private SopAssignmentServiceImpl assignments;
    private SopLearningServiceImpl learning;
    private Staff trainer, aisyah;
    private SopViewer owner;
    /** Module 1: approved material and a 3-question approved quiz. Module 2: approved, with 8 questions. */
    private SopModule shortModule, fullModule;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        courses = store.wire(new SopServiceImpl());
        assignments = store.wire(new SopAssignmentServiceImpl());
        learning = store.wire(new SopLearningServiceImpl());
        trainer = store.person("Tan Wei Ming", null);
        aisyah = store.person("Nur Aisyah", null);
        owner = new SopViewer(trainer.getId(), false);
        store.doc(1L, "Cash Handling", trainer.getId());
        shortModule = store.module(1L, ReviewStatus.APPROVED, SopTestStore.quiz(3, ReviewStatus.APPROVED));
        fullModule = store.module(1L, ReviewStatus.APPROVED, SopTestStore.quiz(8, ReviewStatus.APPROVED));
    }

    private static void assertRefused(String message, Executable call) {
        BadRequestException e = assertThrows(BadRequestException.class, call);
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    private static SopQuizQuestionDto question(String text) {
        SopQuizQuestionDto dto = new SopQuizQuestionDto();
        dto.setQuestionType(QuizQuestionType.MULTIPLE_CHOICE);
        dto.setQuestionText(text);
        dto.setOptions(List.of("A", "B", "C", "D"));
        dto.setCorrectAnswer("B");
        dto.setExplanation("Because B");
        return dto;
    }

    private static SopQuizSettingsRequest perAttempt(Integer questions) {
        SopQuizSettingsRequest request = new SopQuizSettingsRequest();
        request.setQuestionsPerAttempt(questions);
        return request;
    }

    private SopAssignableCourseDto listed() {
        return assignments.listCourses(trainer.getId(), false).stream().filter(c -> c.getId() == 1L).findFirst().orElseThrow();
    }

    private void aisyahIsTakingIt() {
        store.enrollment(1L, aisyah.getId(), SopAssignmentMode.IMMEDIATE, store.today(), store.today().plusDays(14), 14, 70);
    }

    private List<SopDocument> drafts() {
        return store.docs.values().stream().filter(SopDocument::isDraft).toList();
    }

    @Test
    @DisplayName("A quiz with fewer than five questions can't be approved, and says how many more it needs (FR-08-14)")
    void approvalNeedsFiveQuestions() {
        store.doc(2L, "Coin Handling", trainer.getId());
        SopModule m = store.module(2L, ReviewStatus.PENDING, SopTestStore.quiz(3, ReviewStatus.PENDING));

        assertRefused("This quiz has 3 questions. Add 2 more before approving it: every quiz needs at least 5.",
                () -> courses.approveModuleQuiz(m.getId(), owner));
        assertTrue(store.questionsOf(m.getId()).stream().allMatch(q -> q.getReviewStatus() == ReviewStatus.PENDING));

        courses.addQuestion(m.getId(), question("Fourth question?"), owner);
        courses.addQuestion(m.getId(), question("Fifth question?"), owner);
        SopModuleDto approved = courses.approveModuleQuiz(m.getId(), owner);

        assertEquals(5, approved.getQuiz().size());
        assertTrue(approved.getQuiz().stream().allMatch(q -> q.getReviewStatus() == ReviewStatus.APPROVED));
    }

    @Test
    @DisplayName("A course with a short quiz can't be assigned or made Open until the quiz has five questions (FR-09-10)")
    void shortQuizKeepsTheCourseFromStaff() {
        assertFalse(listed().isReady());
        assertEquals(1, listed().getShortQuizzes());
        SopAssignmentRequest request = new SopAssignmentRequest();
        request.setSopDocumentId(1L);
        assertRefused("This course can't be assigned yet. 1 quiz has fewer than 5 questions. Add questions to it first, "
                + "in a new draft version if staff are taking the course.", () -> assignments.assign(request, trainer.getId(), false));
        assertRefused("This course can't be made Open yet. 1 quiz has fewer than 5 questions.",
                () -> assignments.setVisibility(1L, SopVisibility.OPEN, trainer.getId(), false));
        assertDoesNotThrow(() -> assignments.setVisibility(1L, SopVisibility.PRIVATE, trainer.getId(), false));

        courses.addQuestion(shortModule.getId(), question("Fourth question?"), owner);
        courses.addQuestion(shortModule.getId(), question("Fifth question?"), owner);
        courses.approveModuleQuiz(shortModule.getId(), owner);

        assertTrue(listed().isReady());
        assertEquals(0, listed().getShortQuizzes());
        assertEquals(SopVisibility.OPEN, assignments.setVisibility(1L, SopVisibility.OPEN, trainer.getId(), false).getVisibility());
    }

    @Test
    @DisplayName("Staff already taking a course with a short quiz carry on taking it (decision A)")
    void staffAlreadyTakingItCarryOn() {
        aisyahIsTakingIt();
        learning.start(1L, aisyah.getId());
        learning.material(shortModule.getId(), aisyah.getId());

        assertEquals(3, learning.quiz(shortModule.getId(), aisyah.getId()).size());
        Map<Long, String> answers = new HashMap<>();
        for (SopQuizQuestion q : store.questionsOf(shortModule.getId())) {
            answers.put(q.getId(), q.getCorrectAnswer());
        }
        SopQuizSubmission submission = new SopQuizSubmission();
        submission.setAnswers(answers);
        SopQuizResultDto result = learning.submit(shortModule.getId(), submission, aisyah.getId());

        assertTrue(result.isPassed());
    }

    @Test
    @DisplayName("Questions per attempt: five by default, between five and the quiz's size, and recorded (FR-08-15)")
    void questionsPerAttempt() {
        assertEquals(5, courses.getModule(fullModule.getId(), owner).getQuestionsPerAttempt());

        assertEquals(7, courses.updateQuizSettings(fullModule.getId(), perAttempt(7), owner).getQuestionsPerAttempt());
        courses.updateQuizSettings(fullModule.getId(), perAttempt(7), owner); // unchanged: nothing recorded

        assertEquals(List.of("Questions per attempt changed from 5 to 7."), courses.getQuizHistory(fullModule.getId(), owner)
                .stream().map(e -> e.getDetail()).toList());
        for (Integer wrong : new Integer[]{4, 9, null}) {
            assertRefused("Questions per attempt must be between 5 and 8, the number of questions in this quiz.",
                    () -> courses.updateQuizSettings(fullModule.getId(), perAttempt(wrong), owner));
        }
        assertRefused("This quiz has 3 questions. Add 2 more first: each attempt presents at least 5.",
                () -> courses.updateQuizSettings(shortModule.getId(), perAttempt(5), owner));
        assertEquals(7, store.modules.get(fullModule.getId()).getQuestionsPerAttempt());
    }

    @Test
    @DisplayName("Changing questions per attempt on a course staff are taking applies to it directly; a draft keeps the value (decision B)")
    void questionsPerAttemptWhileStaffTakeIt() {
        aisyahIsTakingIt();

        courses.updateQuizSettings(fullModule.getId(), perAttempt(6), owner);

        assertTrue(drafts().isEmpty(), "no draft for a setting");
        assertEquals(6, store.modules.get(fullModule.getId()).getQuestionsPerAttempt());

        courses.addQuestion(fullModule.getId(), question("A new question?"), owner); // a quiz change starts a draft
        SopDocument draft = drafts().get(0);
        SopModule copy = store.modules.values().stream()
                .filter(m -> m.getSopDocumentId().equals(draft.getId()) && m.getModuleOrder() == 2).findFirst().orElseThrow();
        assertEquals(6, copy.getQuestionsPerAttempt());
    }

    @Test
    @DisplayName("Deleting a question removes it, is recorded, and lowers questions per attempt when it has to (FR-08-11)")
    void deleteQuestion() {
        courses.updateQuizSettings(fullModule.getId(), perAttempt(8), owner);
        SopQuizQuestion last = store.questionsOf(fullModule.getId()).get(7);

        SopModuleDto after = courses.deleteQuestion(last.getId(), owner);

        assertEquals(7, after.getQuiz().size());
        assertEquals(7, after.getQuestionsPerAttempt(), "no more than the quiz has left");
        assertEquals("Deleted: " + last.getQuestionText(), courses.getQuizHistory(fullModule.getId(), owner).get(0).getDetail());

        SopModuleDto shorter = courses.deleteQuestion(store.questionsOf(shortModule.getId()).get(0).getId(), owner);
        assertEquals(2, shorter.getQuiz().size());
        assertEquals(5, shorter.getQuestionsPerAttempt(), "never below five");
    }

    @Test
    @DisplayName("Once staff take the course, a deletion goes into a new draft and the live quiz keeps the question")
    void deleteWhileStaffTakeIt() {
        aisyahIsTakingIt();
        SopQuizQuestion first = store.questionsOf(fullModule.getId()).get(0);

        SopModuleDto after = courses.deleteQuestion(first.getId(), owner);

        SopDocument draft = drafts().get(0);
        assertEquals(draft.getId(), after.getSopDocumentId());
        assertEquals(7, after.getQuiz().size());
        assertEquals(8, store.questionsOf(fullModule.getId()).size(), "the live quiz is untouched");
        assertEquals(List.of("EDITED", "COPIED"), courses.getQuizHistory(after.getId(), owner).stream()
                .map(e -> e.getAction().name()).toList());
    }

    @Test
    @DisplayName("A draft can't be published while one of its quizzes is short")
    void publishNeedsFiveQuestions() {
        aisyahIsTakingIt();
        courses.addQuestion(shortModule.getId(), question("Fourth question?"), owner); // starts draft v2
        SopDocument draft = drafts().get(0);
        SopModule draftShort = store.modules.values().stream()
                .filter(m -> m.getSopDocumentId().equals(draft.getId()) && m.getModuleOrder() == 1).findFirst().orElseThrow();
        courses.approveModuleQuiz(store.modules.values().stream()
                .filter(m -> m.getSopDocumentId().equals(draft.getId()) && m.getModuleOrder() == 2).findFirst().orElseThrow().getId(), owner);

        assertRefused("This quiz has 4 questions. Add 1 more before approving it", () -> courses.approveModuleQuiz(draftShort.getId(), owner));
        assertRefused("This draft can't be published yet.", () -> courses.publishDraft(draft.getId(), false, owner));

        courses.addQuestion(draftShort.getId(), question("Fifth question?"), owner);
        courses.approveModuleQuiz(draftShort.getId(), owner);
        assertDoesNotThrow(() -> courses.publishDraft(draft.getId(), false, owner));
    }
}
