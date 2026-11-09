package com.tbm.careerpathlearning.service.impl;

import com.tbm.careerpathlearning.enums.ReviewStatus;
import com.tbm.careerpathlearning.enums.SopAssignmentMode;
import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.exception.InternalServerException;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopMaterialReviewEvent;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.model.SopQuizQuestion;
import com.tbm.careerpathlearning.model.Staff;
import com.tbm.careerpathlearning.service.FakeGeminiServer;
import com.tbm.careerpathlearning.service.LocalSopGenerator;
import com.tbm.careerpathlearning.service.SensitiveDataFilter;
import com.tbm.careerpathlearning.service.SopGenerationService;
import com.tbm.careerpathlearning.service.SopViewer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * The whole generation flow with the real Gemini client against a fake server, so these tests read what would really
 * leave APEX on every path: planning, module content, both rewrites, drafts, and the offline fallback (FR-06-17).
 */
class SopGenerationMaskingTest {

    private static final List<String> PERSONAL = List.of("Ahmad bin Ali", "900101-14-5678", "A12345678", "012-345 6789",
            "someone@example.com", "1234567890");

    private static final String SOP_TEXT = """
            1. Opening the Drawer
            Count the float at the start of every shift. Never leave the drawer open when you step away.
            Report any shortage to the supervisor immediately. Sign the cash log after the count.
            Name: Ahmad bin Ali
            IC No: 900101-14-5678

            2. Coin Tray
            Keep coins in the left tray and notes in the right. Give change in notes first, then in coins.
            Lock the cash drawer before every break. Call 012-345 6789 or write to someone@example.com about the keys.
            Passport No: A12345678

            3. Receiving Payments
            Print a receipt for every payment. Write the guest's room number on the card slip.
            File the slips in the night audit folder. Refunds are paid from the float.
            Account No: 1234567890
            """;

    private static final String CLEAN_TEXT = """
            1. Opening the Drawer
            Count the float at the start of every shift. Never leave the drawer open when you step away.
            Report any shortage to the supervisor immediately. Write to finance@tbm.net or call 03-7890 1234.

            2. Receiving Payments
            Print a receipt for every payment. Write the guest's room number on the card slip.
            Record the customer's IC number on the form. File the slips in the night audit folder.
            """;

    private static final String PLAN = "{\"modules\":[{\"title\":\"Opening the Drawer\",\"covers\":\"Section 1\"},"
            + "{\"title\":\"Coins and Payments\",\"covers\":\"Sections 2 and 3\"}]}";

    /** A module with five questions, different for each call so that they aren't dropped as repeats. */
    private String aiModule(int n) {
        String questions = IntStream.range(0, 5).mapToObj(i -> "{\"type\":\"MULTIPLE_CHOICE\",\"question\":\"Step " + (char) ('a' + n) + (char) ('a' + i)
                        + " check " + n * 10 + i + " counted?\",\"options\":[\"In the log\",\"Nowhere\",\"On the receipt\",\"In an email\"],"
                        + "\"answer\":\"In the log\",\"explanation\":\"SOP\",\"difficulty\":\"EASY\"}")
                .collect(Collectors.joining(","));
        return "{\"title\":\"Module " + n + "\",\"summary\":\"s\",\"learningObjectives\":[],\"tools\":[],"
                + "\"sections\":[{\"type\":\"paragraph\",\"heading\":\"Part\",\"body\":\"Count the float and sign the log.\"}],"
                + "\"keyTerms\":[],\"quiz\":[" + questions + "]}";
    }

    /** Answers like Gemini: a plan for the planning prompt, then a module for each other prompt. */
    private Function<String, String> geminiWorks() {
        int[] modules = {0};
        return prompt -> prompt.contains("break it into a") ? PLAN : aiModule(++modules[0]);
    }

    private FakeGeminiServer gemini;
    private FakeGeminiServer.RecordingClient client;
    private SopTestStore store;
    private SopGenerationService generation;
    private SopServiceImpl courses;
    private Staff trainer;
    private SopViewer owner;

    @BeforeEach
    void setUp() throws IOException {
        gemini = new FakeGeminiServer();
        client = gemini.client();
        store = new SopTestStore();
        generation = store.wire(new SopGenerationService());
        ReflectionTestUtils.setField(generation, "geminiClient", client);
        ReflectionTestUtils.setField(generation, "localSopGenerator", new LocalSopGenerator());
        ReflectionTestUtils.setField(generation, "self", generation);
        courses = store.wire(new SopServiceImpl());
        ReflectionTestUtils.setField(courses, "sopGenerationService", generation);
        trainer = store.person("Tan Wei Ming", null);
        owner = new SopViewer(trainer.getId(), false);
        gemini.answer = geminiWorks();
    }

    @AfterEach
    void tearDown() {
        gemini.close();
    }

    private SopDocument uploaded(long id, String title, String text) {
        SopDocument doc = store.doc(id, title, trainer.getId());
        doc.setGenerationStatus(SopGenerationStatus.PENDING);
        doc.setExtractedText(text);
        return doc;
    }

    private List<SopModule> modulesOf(long docId) {
        return store.modules.values().stream().filter(m -> m.getSopDocumentId().equals(docId))
                .sorted(Comparator.comparing(SopModule::getModuleOrder)).toList();
    }

    /** Everything a person can read of a generated course: titles, content and every part of every question. */
    private String everythingIn(long docId) {
        List<String> parts = new ArrayList<>();
        for (SopModule m : modulesOf(docId)) {
            parts.add(m.getTitle());
            parts.add(m.getContent());
            for (SopQuizQuestion q : store.questionsOf(m.getId())) {
                parts.add(q.getQuestionText());
                parts.add(q.getOptions());
                parts.add(q.getCorrectAnswer());
                parts.add(q.getExplanation());
            }
        }
        return String.join("\n", parts);
    }

    private void assertNothingPersonalWasSent() {
        assertFalse(gemini.bodies.isEmpty(), "the AI should have been asked");
        for (String body : gemini.bodies) {
            for (String value : PERSONAL) {
                assertFalse(body.contains(value), "sent to the AI: " + value);
            }
        }
    }

    private void assertNothingPersonalIn(String text) {
        for (String value : PERSONAL) {
            assertFalse(text.contains(value), "found in the course: " + value + "\n" + text);
        }
    }

    private static void assertMentionsNeitherAiNorOffline(String content) {
        assertFalse(content.contains("\"source\""), content);
        assertFalse(content.contains("offline"), content);
    }

    @Test
    @DisplayName("Generating with the AI: no request carries personal data, and the stored text stays as uploaded")
    void aiGeneration() {
        uploaded(1L, "Cash Handling", SOP_TEXT);

        generation.generateForDocument(1L);

        SopDocument doc = store.docs.get(1L);
        assertEquals(SopGenerationStatus.COMPLETED, doc.getGenerationStatus());
        assertNull(doc.getStatusMessage());
        assertEquals(3, gemini.bodies.size(), "one plan and two modules");
        assertNothingPersonalWasSent();
        for (String prompt : gemini.prompts) {
            assertTrue(prompt.contains("IC No: [IC_NUMBER]"), prompt);
            assertTrue(prompt.contains("Name: [NAME]"), prompt);
            assertTrue(prompt.contains("Note: personal details in this text are hidden"), "told what the labels are");
            assertTrue(prompt.contains("Count the float at the start of every shift."), "the SOP itself is still sent");
        }
        assertEquals(SOP_TEXT, doc.getExtractedText());
        assertEquals(2, modulesOf(1L).size());
    }

    @Test
    @DisplayName("When the AI can't plan: the offline course is masked too, and nothing says it was built offline")
    void offlineGeneration() {
        uploaded(1L, "Cash Handling", SOP_TEXT);
        gemini.answer = prompt -> null;

        generation.generateForDocument(1L);

        SopDocument doc = store.docs.get(1L);
        assertEquals(SopGenerationStatus.COMPLETED, doc.getGenerationStatus());
        assertNull(doc.getStatusMessage(), "no note on the upload list");
        assertNothingPersonalWasSent();
        assertEquals(SOP_TEXT, doc.getExtractedText());
        List<SopModule> modules = modulesOf(1L);
        assertFalse(modules.isEmpty());
        String course = everythingIn(1L);
        assertNothingPersonalIn(course);
        assertTrue(course.contains("[IC_NUMBER]"), "the masked text was used: " + course);
        for (SopModule m : modules) {
            assertMentionsNeitherAiNorOffline(m.getContent());
            assertEquals(List.of(SopGenerationService.GENERATED_DETAIL), store.materialEvents.values().stream()
                    .filter(e -> e.getSopModuleId().equals(m.getId())).map(SopMaterialReviewEvent::getDetail).toList());
            assertEquals(ReviewStatus.PENDING, m.getReviewStatus(), "the trainer still reviews it");
        }
    }

    @Test
    @DisplayName("When the AI can't be reached at all: the course is still built, from the masked text")
    void aiUnreachable() {
        uploaded(1L, "Cash Handling", SOP_TEXT);
        ReflectionTestUtils.setField(client, "baseUrl", "http://127.0.0.1:9");

        generation.generateForDocument(1L);

        SopDocument doc = store.docs.get(1L);
        assertEquals(SopGenerationStatus.COMPLETED, doc.getGenerationStatus());
        assertNull(doc.getStatusMessage());
        assertFalse(modulesOf(1L).isEmpty());
        assertNothingPersonalIn(everythingIn(1L));
        assertTrue(everythingIn(1L).contains("[IC_NUMBER]"));
    }

    @Test
    @DisplayName("When the AI fails part-way: the rest is built from the masked text, with no note and no flag")
    void aiFailsPartWay() {
        uploaded(1L, "Cash Handling", SOP_TEXT);
        int[] moduleCalls = {0};
        gemini.answer = prompt -> prompt.contains("break it into a") ? PLAN : ++moduleCalls[0] == 1 ? aiModule(1) : null;

        generation.generateForDocument(1L);

        SopDocument doc = store.docs.get(1L);
        assertEquals(SopGenerationStatus.COMPLETED, doc.getGenerationStatus());
        assertNull(doc.getStatusMessage());
        assertEquals(2, modulesOf(1L).size());
        assertNothingPersonalWasSent();
        assertNothingPersonalIn(everythingIn(1L));
        modulesOf(1L).forEach(m -> assertMentionsNeitherAiNorOffline(m.getContent()));
        assertEquals(1, store.materialEvents.values().stream().map(SopMaterialReviewEvent::getDetail).distinct().count(),
                "AI and offline modules read the same in their histories");
    }

    @Test
    @DisplayName("A course title with personal data is masked for the AI and for the offline builder")
    void titles() {
        String noHeadings = "Count the float at the start of every shift and sign the cash log straight after the count every day. "
                + "Lock the cash drawer before every break and hand the key to the duty manager before leaving the counter. "
                + "Print a receipt for every payment and file the card slips in the night audit folder at the end of the day.";
        uploaded(1L, "Cash SOP for someone@example.com", noHeadings);

        generation.generateForDocument(1L);
        assertNothingPersonalWasSent();

        gemini.bodies.clear();
        gemini.answer = prompt -> null;
        uploaded(2L, "Cash SOP for someone@example.com", noHeadings);
        generation.generateForDocument(2L);
        assertNothingPersonalWasSent();
        assertFalse(modulesOf(2L).isEmpty());
        assertNothingPersonalIn(everythingIn(2L));
    }

    @Test
    @DisplayName("Rejecting a module or quiz: the SOP text and the trainer's reason are masked in the rewrite request")
    void rewrites() {
        uploaded(1L, "Cash Handling", SOP_TEXT);
        generation.generateForDocument(1L);
        Long moduleId = modulesOf(1L).get(0).getId();
        gemini.bodies.clear();
        gemini.prompts.clear();

        courses.rejectModule(moduleId, "Remove 900101-14-5678 and call 012-345 6789 for details", owner);

        assertEquals(1, gemini.bodies.size());
        assertNothingPersonalWasSent();
        assertTrue(gemini.prompts.get(0).contains("Remove [IC_NUMBER] and call [MOBILE_NUMBER] for details"), gemini.prompts.get(0));
        assertTrue(gemini.prompts.get(0).contains("Name: [NAME]"), gemini.prompts.get(0));

        gemini.bodies.clear();
        gemini.prompts.clear();
        courses.rejectModuleQuiz(moduleId, "Ask about someone@example.com instead", owner);

        assertEquals(1, gemini.bodies.size());
        assertNothingPersonalWasSent();
        assertTrue(gemini.prompts.get(0).contains("Ask about [EMAIL] instead"), gemini.prompts.get(0));
    }

    @Test
    @DisplayName("Rewriting the quiz of a module saved before this filter existed: its old content is masked on the way out")
    void oldContent() {
        uploaded(1L, "Cash Handling", SOP_TEXT);
        SopModule old = store.module(1L, ReviewStatus.PENDING, ReviewStatus.PENDING);
        old.setContent("{\"summary\":\"Ahmad can be reached on 012-345 6789, IC 900101-14-5678.\",\"sections\":[{\"type\":\"paragraph\",\"heading\":\"Part\","
                + "\"body\":\"Name: Ahmad bin Ali\\nCount the float and sign the log. Mail someone@example.com about it.\"}]}");

        courses.rejectModuleQuiz(old.getId(), "Ask about the float", owner);

        assertEquals(1, gemini.bodies.size());
        assertNothingPersonalWasSent();
        assertTrue(gemini.prompts.get(0).contains("Count the float and sign the log."), gemini.prompts.get(0));
    }

    @Test
    @DisplayName("Rejecting a live course's module: the draft's rewrite request is masked as well")
    void draftRewrite() {
        uploaded(1L, "Cash Handling", SOP_TEXT);
        generation.generateForDocument(1L);
        SopModule module = modulesOf(1L).get(0);
        courses.approveModule(module.getId(), owner);
        store.enrollment(1L, store.person("Nur Aisyah", null).getId(), SopAssignmentMode.IMMEDIATE, store.today(), store.today().plusDays(14), 14, 70);
        gemini.bodies.clear();

        courses.rejectModule(module.getId(), "Remove 900101-14-5678", owner);

        assertTrue(store.docs.values().stream().anyMatch(SopDocument::isDraft), "the rewrite went into a draft");
        assertEquals(1, gemini.bodies.size());
        assertNothingPersonalWasSent();
    }

    @Test
    @DisplayName("If the filter fails while generating: nothing is sent, the upload shows Failed and its text is kept")
    void filterFailsWhileGenerating() {
        uploaded(1L, "Cash Handling", SOP_TEXT);

        try (MockedStatic<SensitiveDataFilter> filter = mockStatic(SensitiveDataFilter.class)) {
            filter.when(() -> SensitiveDataFilter.mask(any()))
                    .thenThrow(new IllegalStateException("The text could not be checked for personal data, so nothing was sent to the AI."));
            generation.generateForDocument(1L);
        }

        SopDocument doc = store.docs.get(1L);
        assertEquals(SopGenerationStatus.FAILED, doc.getGenerationStatus());
        assertEquals("The text could not be checked for personal data, so nothing was sent to the AI.", doc.getStatusMessage());
        assertTrue(gemini.bodies.isEmpty(), "nothing was sent");
        assertTrue(modulesOf(1L).isEmpty(), "nothing was built");
        assertEquals(SOP_TEXT, doc.getExtractedText());
    }

    @Test
    @DisplayName("If the filter fails during a rewrite: nothing is sent, the usual error shows and the module is unchanged")
    void filterFailsDuringRewrite() {
        uploaded(1L, "Cash Handling", SOP_TEXT);
        generation.generateForDocument(1L);
        SopModule module = modulesOf(1L).get(0);
        String before = module.getContent();
        gemini.bodies.clear();

        try (MockedStatic<SensitiveDataFilter> filter = mockStatic(SensitiveDataFilter.class)) {
            filter.when(() -> SensitiveDataFilter.mask(any())).thenThrow(new IllegalStateException("could not be checked"));
            assertThrows(InternalServerException.class, () -> courses.rejectModule(module.getId(), "Shorter please", owner));
        }

        assertTrue(gemini.bodies.isEmpty(), "nothing was sent");
        assertEquals(before, store.modules.get(module.getId()).getContent());
        assertEquals(ReviewStatus.PENDING, store.modules.get(module.getId()).getReviewStatus());
    }

    @Test
    @DisplayName("A SOP with nothing personal: every prompt on every path is sent exactly as it is built, with no note")
    void cleanSopIsSentAsBefore() {
        uploaded(1L, "Cash Handling", CLEAN_TEXT);

        generation.generateForDocument(1L);
        Long moduleId = modulesOf(1L).get(0).getId();
        courses.rejectModule(moduleId, "Shorter please", owner);
        courses.rejectModuleQuiz(moduleId, "Ask about cash drops", owner);

        assertEquals(5, gemini.prompts.size(), "plan, two modules, a module rewrite and a quiz rewrite");
        assertEquals(client.given, gemini.prompts, "our prompt wording never trips a rule, so nothing is changed or added");
        assertTrue(gemini.prompts.get(0).contains("Record the customer's IC number on the form."));
    }
}
