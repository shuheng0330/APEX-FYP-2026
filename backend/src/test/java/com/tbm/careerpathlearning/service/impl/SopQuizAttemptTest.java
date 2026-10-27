package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopLearningModuleDto;
import com.tbm.careerpathlearning.dto.SopLearningQuestionDto;
import com.tbm.careerpathlearning.dto.SopQuizResultDto;
import com.tbm.careerpathlearning.dto.SopQuizSubmission;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Taking a quiz: random draws, the pass requirement, results after a fail, and retakes (FR-11-04, FR-11-12 to FR-11-15). */
class SopQuizAttemptTest {

    private SopTestStore store;
    private SopLearningServiceImpl learning;
    private Staff trainer, aisyah;
    /** Module 1 of course 1: 8 approved questions, 5 per attempt. */
    private SopModule module;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        learning = store.wire(new SopLearningServiceImpl());
        ReflectionTestUtils.setField(learning, "random", new Random(7));
        trainer = store.person("Tan Wei Ming", null);
        aisyah = store.person("Nur Aisyah", null);
        store.doc(1L, "Cash Handling", trainer.getId());
        module = store.module(1L, 8);
        store.module(1L, 5);
        store.enrollment(1L, aisyah.getId(), SopAssignmentMode.IMMEDIATE, store.today(), store.today().plusDays(14), 14, 70);
        learning.start(1L, aisyah.getId());
    }

    private static void assertRefused(String message, Executable call) {
        BadRequestException e = assertThrows(BadRequestException.class, call);
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    private SopLearningModuleDto firstModule() {
        return learning.course(1L, aisyah.getId()).getModules().get(0);
    }

    /** Answers the drawn questions, getting {@code right} of them correct, plus every other question of the module. */
    private SopQuizSubmission answers(List<SopLearningQuestionDto> drawn, int right) {
        Map<Long, String> answers = new HashMap<>();
        for (SopQuizQuestion q : store.questionsOf(module.getId())) {
            answers.put(q.getId(), q.getCorrectAnswer()); // questions not drawn are ignored
        }
        for (int i = right; i < drawn.size(); i++) {
            answers.put(drawn.get(i).getId(), "Wrong option");
        }
        SopQuizSubmission submission = new SopQuizSubmission();
        submission.setAnswers(answers);
        return submission;
    }

    /** A minute later: open the material, draw a quiz and answer {@code right} of its questions correctly. */
    private SopQuizResultDto attempt(int right) {
        store.clock.advance(Duration.ofMinutes(1));
        learning.material(module.getId(), aisyah.getId());
        List<SopLearningQuestionDto> drawn = learning.quiz(module.getId(), aisyah.getId());
        return learning.submit(module.getId(), answers(drawn, right), aisyah.getId());
    }

    @Test
    @DisplayName("Before starting, the learner sees how many questions there are and how many correct answers pass (FR-11-12)")
    void passRequirementShownFirst() {
        SopLearningModuleDto m = firstModule();
        assertEquals(5, m.getQuizSize());
        assertEquals(4, m.getPassCorrect(), "70% of 5 is 3.5, so 4 correct");
        assertEquals(3, m.getTriesLeftToday());
        assertFalse(m.isReviewMaterialFirst());

        assertEquals(4, SopLearningServiceImpl.passCorrect(70, 5));
        assertEquals(3, SopLearningServiceImpl.passCorrect(70, 4));
        assertEquals(5, SopLearningServiceImpl.passCorrect(100, 5));
        assertEquals(3, SopLearningServiceImpl.passCorrect(67, 3), "two of three is 66.7%, below 67%");
    }

    @Test
    @DisplayName("Each attempt draws its questions at random in a new order, with shuffled options, and is marked on them (FR-11-14)")
    void randomDraws() {
        List<Long> all = store.questionsOf(module.getId()).stream().map(SopQuizQuestion::getId).toList();
        Set<List<Long>> orders = new HashSet<>();
        Set<List<String>> optionOrders = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            List<SopLearningQuestionDto> drawn = learning.quiz(module.getId(), aisyah.getId());
            assertEquals(5, drawn.size());
            assertTrue(all.containsAll(drawn.stream().map(SopLearningQuestionDto::getId).toList()));
            assertEquals(5, drawn.stream().map(SopLearningQuestionDto::getId).distinct().count());
            orders.add(drawn.stream().map(SopLearningQuestionDto::getId).toList());
            SopLearningQuestionDto q = drawn.get(0);
            assertEquals(4, q.getOptions().size());
            SopQuizQuestion stored = store.questions.get(q.getId());
            optionOrders.add(q.getOptions());
            assertTrue(q.getOptions().contains(stored.getCorrectAnswer()));
        }
        assertTrue(orders.size() > 1, "the questions change between attempts");
        assertTrue(optionOrders.size() > 1, "the options are shuffled");

        SopQuizResultDto result = attempt(5);
        assertEquals(5, result.getTotal(), "marked on the 5 drawn questions, not all 8");
        assertEquals(5, result.getCorrect());
        assertTrue(result.isPassed());
        assertEquals(4, result.getPassCorrect());
    }

    @Test
    @DisplayName("Four of five passes at 70%; three of five fails")
    void passMarkAsCorrectAnswers() {
        SopQuizResultDto fail = attempt(3);
        assertFalse(fail.isPassed());
        assertEquals(60, fail.getScore());
        SopQuizResultDto pass = attempt(4);
        assertTrue(pass.isPassed());
        assertEquals(80, pass.getScore());
    }

    @Test
    @DisplayName("A submission needs a quiz opened first, and a submitted draw can't be marked twice")
    void submitNeedsADraw() {
        SopQuizSubmission none = new SopQuizSubmission();
        none.setAnswers(Map.of());
        assertRefused("Open it again to start a new attempt", () -> learning.submit(module.getId(), none, aisyah.getId()));

        attempt(1);
        store.clock.advance(Duration.ofMinutes(1));
        learning.material(module.getId(), aisyah.getId()); // reviewed, but the marked draw is gone
        assertRefused("Open it again", () -> learning.submit(module.getId(), none, aisyah.getId()));
    }

    @Test
    @DisplayName("After a fail, only the score shows, and a retake needs the material reopened (FR-11-13, FR-11-04)")
    void failShowsScoreOnly() {
        SopQuizResultDto fail = attempt(2);

        assertFalse(fail.isPassed());
        assertEquals(40, fail.getScore());
        assertEquals(4, fail.getPassCorrect());
        assertTrue(fail.getQuestions().isEmpty(), "not which answers were wrong");
        assertEquals(2, fail.getTriesLeftToday());
        assertTrue(firstModule().isReviewMaterialFirst());
        store.clock.advance(Duration.ofMinutes(1));
        assertRefused("Review the module's material again before you retake the quiz.",
                () -> learning.quiz(module.getId(), aisyah.getId()));

        store.clock.advance(Duration.ofMinutes(1));
        learning.material(module.getId(), aisyah.getId());
        assertFalse(firstModule().isReviewMaterialFirst());
        assertEquals(5, learning.quiz(module.getId(), aisyah.getId()).size());
    }

    @Test
    @DisplayName("Three tries per module per Malaysia day, then the quiz opens again the next day (FR-11-04)")
    void threeTriesADay() {
        attempt(1);
        attempt(1);
        SopQuizResultDto third = attempt(1);
        assertEquals(0, third.getTriesLeftToday());
        assertEquals(0, firstModule().getTriesLeftToday());

        store.clock.advance(Duration.ofMinutes(1));
        learning.material(module.getId(), aisyah.getId());
        assertRefused("You've used today's 3 tries at this quiz. You can try again tomorrow.",
                () -> learning.quiz(module.getId(), aisyah.getId()));

        // 14:00 in Malaysia is still the same day; midnight there starts a new one.
        store.clock.advance(Duration.ofHours(14));
        assertEquals(3, firstModule().getTriesLeftToday());
        assertTrue(attempt(5).isPassed());
    }

    @Test
    @DisplayName("The trainer is told once when a learner fails the same quiz three times (FR-11-15)")
    void trainerToldAfterThreeFails() {
        attempt(1);
        attempt(1);
        assertTrue(store.notifications.isEmpty());

        attempt(1);

        assertEquals(1, store.notifications.size());
        SopTestStore.Sent sent = store.notifications.get(0);
        assertEquals(trainer.getId(), sent.recipient());
        assertEquals(NotificationType.QUIZ_FAILED_THREE_TIMES, sent.type());
        assertEquals("Nur Aisyah has failed the quiz of module 1, Module 1, 3 times in Cash Handling.", sent.body());

        store.clock.advance(Duration.ofDays(1));
        attempt(1);
        assertEquals(1, store.notifications.size(), "only once");
    }
}
