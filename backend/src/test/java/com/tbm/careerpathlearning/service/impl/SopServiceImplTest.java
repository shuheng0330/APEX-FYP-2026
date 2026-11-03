package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.dto.SopModuleDto;
import com.tbm.careerpathlearning.dto.SopQuizQuestionDto;
import com.tbm.careerpathlearning.enums.NotificationType;
import com.tbm.careerpathlearning.enums.QuizDifficulty;
import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.exception.BadRequestException;
import com.tbm.careerpathlearning.exception.InternalServerException;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.service.GeminiClient;
import com.tbm.careerpathlearning.service.LocalSopGenerator;
import com.tbm.careerpathlearning.service.SopGenerationService;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The trainer's quiz review (FR-08): approve, edit, reject and regenerate, with the review history. */
class SopServiceImplTest {

    /** Answers every prompt with the same three-question quiz, or fails like an exhausted API key. */
    static class FakeGemini extends GeminiClient {
        boolean down;

        @Override
        public String generateJson(String prompt) {
            if (down) {
                throw new RuntimeException("Gemini request failed: 429 credits depleted");
            }
            return "{\"title\":\"Handling Cash\",\"content\":\"x\",\"quiz\":["
                    + "{\"type\":\"MULTIPLE_CHOICE\",\"question\":\"When are cash drops recorded?\",\"options\":[\"Never\",\"In the cash log\",\"Weekly\",\"Monthly\"],\"answer\":\"In the cash log\",\"explanation\":\"SOP 4.3\",\"difficulty\":\"medium\"},"
                    + "{\"type\":\"TRUE_FALSE\",\"question\":\"Leave the drawer open.\",\"options\":[\"True\",\"False\"],\"answer\":\"False\",\"explanation\":\"SOP 4.1\",\"difficulty\":\"EASY\"},"
                    + "{\"type\":\"TRUE_FALSE\",\"question\":\"No difficulty given.\",\"options\":[\"True\",\"False\"],\"answer\":\"True\",\"explanation\":\"x\"}]}";
        }
    }

    private SopTestStore store;
    private SopServiceImpl service;
    private FakeGemini gemini;
    private Staff trainer;
    /** The trainer, signed in as the course's owner. */
    private SopViewer owner;
    private Long moduleId;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        gemini = new FakeGemini();
        SopGenerationService generation = store.wire(new SopGenerationService());
        ReflectionTestUtils.setField(generation, "geminiClient", gemini);
        ReflectionTestUtils.setField(generation, "localSopGenerator", new LocalSopGenerator());
        ReflectionTestUtils.setField(generation, "self", generation);
        service = store.wire(new SopServiceImpl());
        ReflectionTestUtils.setField(service, "sopGenerationService", generation);
        trainer = store.person("Trainer Tan", null);
        owner = new SopViewer(trainer.getId(), false);

        store.doc(1L, "Cash Handling SOP", trainer.getId())
                .setExtractedText("4.1 Handling Cash\nDo not leave the cash drawer unattended.");
        SopModule module = store.module(1L, ReviewStatus.PENDING, SopTestStore.quiz(5, ReviewStatus.PENDING));
        module.setTitle("Handling Cash");
        moduleId = module.getId();
    }

    @Test
    @DisplayName("Approving the quiz approves every question and records who did it")
    void approve() {
        SopModuleDto approved = service.approveModuleQuiz(moduleId, owner);

        assertTrue(approved.getQuiz().stream().allMatch(q -> q.getReviewStatus() == ReviewStatus.APPROVED));
        assertEquals(List.of("APPROVED by Trainer Tan"), history());
    }

    @Test
    @DisplayName("Editing a question saves its difficulty, keeps it approved and records the edit")
    void edit() {
        service.approveModuleQuiz(moduleId, owner);
        SopQuizQuestionDto edit = new SopQuizQuestionDto();
        edit.setQuestionType(QuizQuestionType.MULTIPLE_CHOICE);
        edit.setQuestionText("Edited question one?");
        edit.setOptions(List.of("A", "B", "C"));
        edit.setCorrectAnswer("B");
        edit.setExplanation("because");
        edit.setDifficulty(QuizDifficulty.HARD);

        SopQuizQuestionDto saved = service.updateQuestion(store.questionsOf(moduleId).get(0).getId(), edit, owner);

        assertEquals(QuizDifficulty.HARD, saved.getDifficulty());
        assertEquals(ReviewStatus.APPROVED, saved.getReviewStatus());
        assertTrue(history().get(0).startsWith("EDITED by Trainer Tan: Edited: Edited question one?"));
    }

    @Test
    @DisplayName("Rejecting the quiz with a reason has the AI write a new one, recorded after the rejection")
    void rejectRegenerates() {
        SopModuleDto regenerated = service.rejectModuleQuiz(moduleId, "Add a question on cash drops", owner);

        assertEquals(3, regenerated.getQuiz().size());
        assertTrue(regenerated.getQuiz().stream().allMatch(q -> q.getReviewStatus() == ReviewStatus.REGENERATED));
        assertEquals(QuizDifficulty.MEDIUM, regenerated.getQuiz().get(0).getDifficulty(), "lower-case difficulty is read");
        assertEquals(QuizDifficulty.EASY, regenerated.getQuiz().get(1).getDifficulty());
        assertNull(regenerated.getQuiz().get(2).getDifficulty());
        List<String> history = history();
        assertEquals(2, history.size());
        assertTrue(history.get(0).startsWith("REGENERATED by AI"));
        assertEquals("REJECTED by Trainer Tan: Add a question on cash drops", history.get(1));
        assertThrows(BadRequestException.class, () -> service.rejectModuleQuiz(moduleId, " ", owner),
                "a reason is required");
    }

    @Test
    @DisplayName("Rejecting while the AI is unavailable fails with a readable error and changes nothing")
    void rejectWhileAiIsDown() {
        gemini.down = true;
        Map<Long, ReviewStatus> before = statuses();

        InternalServerException e = assertThrows(InternalServerException.class,
                () -> service.rejectModuleQuiz(moduleId, "Try again", owner));

        assertTrue(e.getMessage().contains("unavailable"), e.getMessage());
        assertTrue(history().isEmpty());
        assertEquals(before, statuses());
    }

    @Test
    @DisplayName("The course owner is told when regenerated content is ready, and not when the AI fails (FR-07-06, FR-08-04)")
    void ownerIsNotified() {
        service.rejectModuleQuiz(moduleId, "Add a question on cash drops", owner);
        service.rejectModule(moduleId, "Shorter please", owner);
        gemini.down = true;
        assertThrows(InternalServerException.class, () -> service.rejectModuleQuiz(moduleId, "Again", owner));

        assertEquals(List.of(NotificationType.QUIZ_REGENERATED, NotificationType.MATERIAL_REGENERATED),
                store.notifications.stream().map(SopTestStore.Sent::type).toList());
        SopTestStore.Sent quiz = store.notifications.get(0);
        assertEquals(trainer.getId(), quiz.recipient());
        assertEquals("Quiz regenerated: Cash Handling SOP", quiz.title());
        assertEquals("Module 1, \"Handling Cash\", has a new quiz to review.", quiz.body());
        assertEquals("/sop/quiz-review/1", quiz.link());
        assertEquals("/sop/review/1", store.notifications.get(1).link());
    }

    @Test
    @DisplayName("Rejecting the module material also regenerates its quiz")
    void materialRejectionRegeneratesTheQuiz() {
        service.rejectModule(moduleId, "Shorter please", owner);

        assertTrue(history().get(0).startsWith("REGENERATED by AI: Regenerated together with the rejected module material."));
    }

    @Test
    @DisplayName("Deleting the SOP removes its modules, questions and review history")
    void deleteRemovesHistory() {
        service.approveModuleQuiz(moduleId, owner);

        service.deleteDocument(1L, owner);

        assertTrue(store.docs.isEmpty());
        assertTrue(store.modules.isEmpty());
        assertTrue(store.questions.isEmpty());
        assertTrue(store.events.isEmpty());
    }

    private List<String> history() {
        return service.getQuizHistory(moduleId, owner).stream()
                .map(e -> e.getAction() + " by " + (e.getActorName() == null ? "AI" : e.getActorName())
                        + (e.getDetail() == null ? "" : ": " + e.getDetail()))
                .toList();
    }

    private Map<Long, ReviewStatus> statuses() {
        return store.questionsOf(moduleId).stream()
                .collect(Collectors.toMap(SopQuizQuestion::getId, SopQuizQuestion::getReviewStatus));
    }
}
