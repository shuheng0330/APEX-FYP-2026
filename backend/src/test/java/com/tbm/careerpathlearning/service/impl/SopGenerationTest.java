package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.service.GeminiClient;
import com.tbm.careerpathlearning.service.LocalSopGenerator;
import com.tbm.careerpathlearning.service.SopGenerationService;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** What the AI is asked for: five-question quizzes, with sections too short for one folded into a neighbour (FR-06-13). */
class SopGenerationTest {

    private static final String QUESTION = "{\"type\":\"MULTIPLE_CHOICE\",\"question\":\"Where is the count recorded?\","
            + "\"options\":[\"In the cash log\",\"Nowhere\",\"On the receipt\",\"In an email\"],\"answer\":\"In the cash log\","
            + "\"explanation\":\"SOP 1\",\"difficulty\":\"EASY\"}";
    private static final String BLANK = "{\"type\":\"FILL_IN_THE_BLANK\",\"question\":\"The count goes in the cash ___.\","
            + "\"options\":[],\"answer\":\"log\",\"explanation\":\"SOP 1\",\"difficulty\":\"EASY\"}";
    private static final String SECTIONS = "\"sections\":[{\"type\":\"paragraph\",\"heading\":\"Float\",\"body\":"
            + "\"Count the float at the start of every shift. Never leave the drawer open when you step away. "
            + "Report any shortage to the supervisor immediately.\"}]";
    private static final String MODULE = module(QUESTION, QUESTION, QUESTION, QUESTION, QUESTION);

    private static String module(String... questions) {
        return "{\"title\":\"Opening the Drawer\",\"summary\":\"s\",\"learningObjectives\":[],\"tools\":[]," + SECTIONS
                + ",\"keyTerms\":[],\"content\":\"x\",\"quiz\":[" + String.join(",", questions) + "]}";
    }

    /** Answers the planning prompt with a two-module plan and every other prompt with a module, keeping every prompt. */
    static class ScriptedGemini extends GeminiClient {
        final List<String> prompts = new ArrayList<>();
        String module = MODULE;

        @Override
        public String generateJson(String prompt) {
            prompts.add(prompt);
            return prompt.contains("break it into a") ? "{\"modules\":[{\"title\":\"Opening the Drawer\",\"covers\":\"Section 1\"},"
                    + "{\"title\":\"Receiving Payments\",\"covers\":\"Sections 2 and 3\"}]}" : module;
        }
    }

    private SopTestStore store;
    private ScriptedGemini gemini;
    private SopGenerationService generation;
    private SopServiceImpl courses;
    private Staff trainer;

    @BeforeEach
    void setUp() {
        store = new SopTestStore();
        gemini = new ScriptedGemini();
        generation = store.wire(new SopGenerationService());
        ReflectionTestUtils.setField(generation, "geminiClient", gemini);
        ReflectionTestUtils.setField(generation, "localSopGenerator", new LocalSopGenerator());
        ReflectionTestUtils.setField(generation, "self", generation);
        courses = store.wire(new SopServiceImpl());
        ReflectionTestUtils.setField(courses, "sopGenerationService", generation);
        trainer = store.person("Tan Wei Ming", null);
        SopDocument doc = store.doc(1L, "Cash Handling", trainer.getId());
        doc.setGenerationStatus(SopGenerationStatus.PENDING);
        doc.setExtractedText("1. Opening the Drawer\nCount the float at the start of every shift.\n\n"
                + "2. Coin Tray\nCoins go in the left tray.\n\n3. Receiving Payments\nCount the cash in front of the guest.");
    }

    @Test
    @DisplayName("Generation asks for at least five multiple-choice and true/false questions, and for short sections to be folded in")
    void newGenerationAsksForFiveQuestions() {
        generation.generateForDocument(1L);

        String plan = gemini.prompts.get(0);
        assertTrue(plan.replaceAll("\\s+", " ").contains("each module needs enough content for a quiz of at least 5 questions"), plan);
        assertTrue(plan.replaceAll("\\s+", " ").contains("fold any section too short for that into a neighbouring module"), plan);
        assertFalse(plan.contains("merge away"), "the old rule against merging is gone");
        String expand = gemini.prompts.get(1).replaceAll("\\s+", " ");
        assertTrue(expand.contains("quiz: at least 5 questions, more where this module's content supports them, of types "
                + "MULTIPLE_CHOICE or TRUE_FALSE only"), expand);

        assertEquals(SopGenerationStatus.COMPLETED, store.docs.get(1L).getGenerationStatus());
        List<SopModule> modules = store.modules.values().stream().filter(m -> m.getSopDocumentId().equals(1L)).toList();
        assertEquals(2, modules.size());
        modules.forEach(m -> assertEquals(5, store.questionsOf(m.getId()).size(), m.getTitle()));
    }

    @Test
    @DisplayName("Regenerating a rejected quiz or module asks for the same, never for fill-in-the-blank (decision C)")
    void regenerationAsksForFiveQuestions() {
        generation.generateForDocument(1L);
        Long moduleId = store.modules.values().stream().filter(m -> m.getSopDocumentId().equals(1L)).findFirst().orElseThrow().getId();
        SopViewer owner = new SopViewer(trainer.getId(), false);
        gemini.prompts.clear();

        courses.rejectModuleQuiz(moduleId, "Ask about cash drops", owner);
        courses.rejectModule(moduleId, "Shorter please", owner);

        String quiz = gemini.prompts.get(0).replaceAll("\\s+", " ");
        assertTrue(quiz.contains("a new quiz of at least 5 questions"), quiz);
        assertTrue(quiz.contains("MULTIPLE_CHOICE with 4 options, or TRUE_FALSE with [\"True\",\"False\"]; no other types"), quiz);
        assertFalse(quiz.contains("FILL_IN_THE_BLANK"), quiz);
        String material = gemini.prompts.get(1).replaceAll("\\s+", " ");
        assertTrue(material.contains("The quiz must have at least 5 questions, more where the content supports them, of types "
                + "MULTIPLE_CHOICE or TRUE_FALSE only"), material);
    }

    @Test
    @DisplayName("A fill-in-the-blank question the AI returns anyway is dropped, and the quiz is topped up from the module's text")
    void otherFormatsAreDropped() {
        gemini.module = module(QUESTION, QUESTION, QUESTION, QUESTION, BLANK);

        generation.generateForDocument(1L);

        List<SopModule> modules = store.modules.values().stream().filter(m -> m.getSopDocumentId().equals(1L)).toList();
        assertFalse(modules.isEmpty());
        for (SopModule m : modules) {
            List<QuizQuestionType> types = store.questionsOf(m.getId()).stream().map(q -> q.getQuestionType()).toList();
            assertFalse(types.contains(QuizQuestionType.FILL_IN_THE_BLANK), types.toString());
            assertEquals(5, types.size(), "the four kept questions, topped up to five: " + types);
        }
    }
}
