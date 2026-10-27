package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.QuizQuestionType;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.service.GeminiClient;
import com.tbm.careerpathlearning.service.LocalSopGenerator;
import com.tbm.careerpathlearning.service.QuestionOverlap;
import com.tbm.careerpathlearning.service.SopGenerationService;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * What the AI is asked for: five-question quizzes, with sections too short for one folded into a neighbour (FR-06-13),
 * and no question another module already asks (test note AR-03).
 */
class SopGenerationTest {

    private static final String BLANK = "{\"type\":\"FILL_IN_THE_BLANK\",\"question\":\"The count goes in the cash ___.\","
            + "\"options\":[],\"answer\":\"log\",\"explanation\":\"SOP 1\",\"difficulty\":\"EASY\"}";
    private static final String PLAN = "{\"modules\":[{\"title\":\"Opening the Drawer\",\"covers\":\"Section 1\"},"
            + "{\"title\":\"Receiving Payments\",\"covers\":\"Sections 2 and 3\"}]}";
    private static final List<String> TOPICS = List.of("float", "drawer", "receipt", "tray");
    /** Each module's text, by what it is about. */
    private static final Map<String, String> TEXT = Map.of(
            "float", "Count the float at the start of every shift. Never leave the drawer open when you step away. "
                    + "Report any shortage to the supervisor immediately.",
            "drawer", "Lock the cash drawer before every break. Keep coins in the left tray and notes in the right. "
                    + "Give change in notes first, then in coins.",
            "receipt", "Print a receipt for every payment. Write the guest's room number on the card slip. "
                    + "File the slips in the night audit folder.",
            "tray", "Empty the coin tray into the safe at closing. Two staff members sign the safe log. "
                    + "Keep the safe key with the duty manager.");

    private static String question(String text) {
        return "{\"type\":\"MULTIPLE_CHOICE\",\"question\":\"" + text + "\",\"options\":[\"In the cash log\",\"Nowhere\","
                + "\"On the receipt\",\"In an email\"],\"answer\":\"In the cash log\",\"explanation\":\"SOP 1\",\"difficulty\":\"EASY\"}";
    }

    /** Five different questions about one thing. */
    private static List<String> fiveAbout(String thing) {
        return Stream.of("counted", "recorded", "reported", "checked", "signed")
                .map(done -> question("When is the " + thing + " " + done + "?")).toList();
    }

    private static String module(String topic, List<String> questions) {
        return "{\"title\":\"Opening the Drawer\",\"summary\":\"s\",\"learningObjectives\":[],\"tools\":[],"
                + "\"sections\":[{\"type\":\"paragraph\",\"heading\":\"Float\",\"body\":\"" + TEXT.get(topic) + "\"}],"
                + "\"keyTerms\":[],\"content\":\"x\",\"quiz\":[" + String.join(",", questions) + "]}";
    }

    /**
     * Answers the planning prompt with a two-module plan and every other prompt with a module about the next topic
     * (the float, then the drawer, ...), whose five questions are about that topic. Keeps every prompt.
     */
    static class ScriptedGemini extends GeminiClient {
        final List<String> prompts = new ArrayList<>();
        /** Every module's questions are about the float, as an AI repeating itself would write them. */
        boolean sameQuestions;
        /** The last of each module's five questions is fill-in-the-blank. */
        boolean withBlank;
        private int modules;

        @Override
        public String generateJson(String prompt) {
            prompts.add(prompt);
            if (prompt.contains("break it into a")) {
                return PLAN;
            }
            String topic = TOPICS.get(modules++ % TOPICS.size());
            List<String> questions = new ArrayList<>(fiveAbout(sameQuestions ? "float" : topic));
            if (withBlank) {
                questions.set(4, BLANK);
            }
            return module(topic, questions);
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

    private List<SopModule> modules() {
        return store.modules.values().stream().filter(m -> m.getSopDocumentId().equals(1L))
                .sorted(Comparator.comparing(SopModule::getModuleOrder)).toList();
    }

    private List<String> questions(SopModule module) {
        return store.questionsOf(module.getId()).stream().map(SopQuizQuestion::getQuestionText).toList();
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
        List<SopModule> modules = modules();
        assertEquals(2, modules.size());
        modules.forEach(m -> assertEquals(5, store.questionsOf(m.getId()).size(), m.getTitle()));
    }

    @Test
    @DisplayName("Regenerating a rejected quiz or module asks for the same, never for fill-in-the-blank (decision C)")
    void regenerationAsksForFiveQuestions() {
        generation.generateForDocument(1L);
        Long moduleId = modules().get(0).getId();
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
        gemini.withBlank = true;

        generation.generateForDocument(1L);

        List<SopModule> modules = modules();
        assertEquals(2, modules.size());
        for (SopModule m : modules) {
            List<QuizQuestionType> types = store.questionsOf(m.getId()).stream().map(q -> q.getQuestionType()).toList();
            assertFalse(types.contains(QuizQuestionType.FILL_IN_THE_BLANK), types.toString());
            assertEquals(5, types.size(), "the four kept questions, topped up to five: " + types);
        }
    }

    @Test
    @DisplayName("A question another module already asks is left out, and the AI is told what the others ask (AR-03)")
    void noRepeatsAcrossModules() {
        gemini.sameQuestions = true;

        generation.generateForDocument(1L);

        List<SopModule> modules = modules();
        List<String> first = questions(modules.get(0)), second = questions(modules.get(1));
        assertEquals(5, first.size(), first.toString());
        assertFalse(second.isEmpty(), "topped up from the module's own text");
        assertTrue(second.stream().noneMatch(q -> first.stream().anyMatch(f -> QuestionOverlap.repeats(q, f))), second.toString());
        assertFalse(gemini.prompts.get(1).contains("Other modules of this course already ask"), "nothing is asked before module 1");
        String expand = gemini.prompts.get(2);
        assertTrue(expand.contains("Other modules of this course already ask these questions"), expand);
        assertTrue(expand.contains("- When is the float counted?"), expand);

        // A quiz the trainer rejects is rewritten without the other module's questions, too.
        gemini.prompts.clear();
        courses.rejectModuleQuiz(modules.get(1).getId(), "Ask about the drawer", new SopViewer(trainer.getId(), false));

        assertTrue(gemini.prompts.get(0).contains("- When is the float recorded?"), gemini.prompts.get(0));
        List<String> rewritten = questions(modules.get(1));
        assertFalse(rewritten.isEmpty());
        assertTrue(rewritten.stream().noneMatch(q -> first.stream().anyMatch(f -> QuestionOverlap.repeats(q, f))), rewritten.toString());
    }
}
