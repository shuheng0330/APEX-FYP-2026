package com.tbm.careerpathlearning.integration;

import com.tbm.careerpathlearning.enums.SopGenerationStatus;
import com.tbm.careerpathlearning.enums.SopVisibility;
import com.tbm.careerpathlearning.model.SopDocument;
import com.tbm.careerpathlearning.model.SopModule;
import com.tbm.careerpathlearning.repository.SopDocumentRepository;
import com.tbm.careerpathlearning.repository.SopMaterialReviewEventRepository;
import com.tbm.careerpathlearning.repository.SopModuleRepository;
import com.tbm.careerpathlearning.service.DocumentParserService;
import com.tbm.careerpathlearning.service.FakeGeminiServer;
import com.tbm.careerpathlearning.service.GeminiClient;
import com.tbm.careerpathlearning.service.SopGenerationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generation in the running application: real beans, transactions and database, and the aspect that logs every method's
 * arguments and results. Only Gemini is replaced. Personal data must reach neither the AI nor the logs (FR-06-17).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SopGenerationMaskingIntegrationTest {

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

    private static final String PLAN = "{\"modules\":[{\"title\":\"Opening the Drawer\",\"covers\":\"Section 1\"},"
            + "{\"title\":\"Coins and Payments\",\"covers\":\"Sections 2 and 3\"}]}";

    @Autowired private SopGenerationService generation;
    @Autowired private GeminiClient geminiClient;
    @Autowired private DocumentParserService parser;
    @Autowired private SopDocumentRepository documents;
    @Autowired private SopModuleRepository modules;
    @Autowired private SopMaterialReviewEventRepository materialHistory;

    private FakeGeminiServer gemini;
    private Object realBaseUrl;
    private Object realDelay;
    private Long docId;

    private String aiModule(int n) {
        String questions = IntStream.range(0, 5).mapToObj(i -> "{\"type\":\"MULTIPLE_CHOICE\",\"question\":\"Step " + (char) ('a' + n) + (char) ('a' + i)
                        + " check " + n * 10 + i + " counted?\",\"options\":[\"In the log\",\"Nowhere\",\"On the receipt\",\"In an email\"],"
                        + "\"answer\":\"In the log\",\"explanation\":\"SOP\",\"difficulty\":\"EASY\"}")
                .collect(Collectors.joining(","));
        return "{\"title\":\"Module " + n + "\",\"summary\":\"s\",\"learningObjectives\":[],\"tools\":[],"
                + "\"sections\":[{\"type\":\"paragraph\",\"heading\":\"Part\",\"body\":\"Count the float and sign the log.\"}],"
                + "\"keyTerms\":[],\"quiz\":[" + questions + "]}";
    }

    private Function<String, String> geminiWorks() {
        int[] calls = {0};
        return prompt -> prompt.contains("break it into a") ? PLAN : aiModule(++calls[0]);
    }

    @BeforeEach
    void setUp() throws IOException {
        gemini = new FakeGeminiServer();
        gemini.answer = geminiWorks();
        realBaseUrl = ReflectionTestUtils.getField(geminiClient, "baseUrl");
        realDelay = ReflectionTestUtils.getField(generation, "callDelayMs");
        ReflectionTestUtils.setField(geminiClient, "baseUrl", gemini.baseUrl());
        ReflectionTestUtils.setField(generation, "callDelayMs", 0L);

        SopDocument doc = new SopDocument();
        doc.setTitle("Cash Handling");
        doc.setUploadedBy(UUID.randomUUID());
        doc.setVisibility(SopVisibility.PRIVATE);
        doc.setGenerationStatus(SopGenerationStatus.PENDING);
        doc.setExtractedText(SOP_TEXT);
        doc.setCreatedAt(OffsetDateTime.now());
        doc.setUpdatedAt(OffsetDateTime.now());
        docId = documents.save(doc).getId();
    }

    @AfterEach
    void tearDown() {
        generation.clearGenerated(docId);
        documents.deleteById(docId);
        ReflectionTestUtils.setField(geminiClient, "baseUrl", realBaseUrl);
        ReflectionTestUtils.setField(generation, "callDelayMs", realDelay);
        gemini.close();
    }

    private List<SopModule> savedModules() {
        return modules.findAllBySopDocumentIdOrderByModuleOrderAsc(docId).stream()
                .sorted(Comparator.comparing(SopModule::getModuleOrder)).toList();
    }

    private void assertNothingPersonalWasSent() {
        assertFalse(gemini.bodies.isEmpty(), "the AI should have been asked");
        for (String body : gemini.bodies) {
            for (String value : PERSONAL) {
                assertFalse(body.contains(value), "sent to the AI: " + value);
            }
        }
    }

    private static void assertNothingPersonalWasLogged(CapturedOutput output) {
        String logs = output.getOut() + output.getErr();
        assertTrue(logs.contains("LoggingAspect") || logs.contains("Entering: com.tbm.careerpathlearning"),
                "the logging aspect should be running, or this test proves nothing");
        for (String value : PERSONAL) {
            assertFalse(logs.contains(value), "printed in the logs: " + value);
        }
    }

    @Test
    @DisplayName("Generating with the AI: nothing personal is sent or logged, and the counts are")
    void aiGeneration(CapturedOutput output) {
        generation.generateForDocument(docId);

        SopDocument doc = documents.findById(docId).orElseThrow();
        assertEquals(SopGenerationStatus.COMPLETED, doc.getGenerationStatus());
        assertNull(doc.getStatusMessage());
        assertEquals(SOP_TEXT, doc.getExtractedText(), "the stored text stays as uploaded");
        assertEquals(2, savedModules().size());
        assertNothingPersonalWasSent();
        assertNothingPersonalWasLogged(output);
        assertTrue(output.getOut().contains("Masked personal data in document " + docId + " before generating: "
                + "{IC_NUMBER=1, PASSPORT_NUMBER=1, MOBILE_NUMBER=1, EMAIL=1, BANK_ACCOUNT=1, NAME=1}"), "the counts are logged");
        assertTrue(output.getOut().contains("Entering: com.tbm.careerpathlearning.service.SopGenerationService.generateForDocument"),
                "other methods are still logged");
    }

    @Test
    @DisplayName("When the AI is unavailable: the offline course and the logs hold nothing personal, and nothing marks it as offline")
    void offlineGeneration(CapturedOutput output) {
        gemini.answer = prompt -> null;

        generation.generateForDocument(docId);

        SopDocument doc = documents.findById(docId).orElseThrow();
        assertEquals(SopGenerationStatus.COMPLETED, doc.getGenerationStatus());
        assertNull(doc.getStatusMessage());
        assertEquals(SOP_TEXT, doc.getExtractedText());
        assertNothingPersonalWasSent();
        assertNothingPersonalWasLogged(output);
        List<SopModule> saved = savedModules();
        assertFalse(saved.isEmpty());
        for (SopModule module : saved) {
            for (String value : PERSONAL) {
                assertFalse(module.getContent().contains(value), value);
            }
            assertFalse(module.getContent().contains("\"source\""), module.getContent());
            assertEquals(List.of(SopGenerationService.GENERATED_DETAIL), materialHistory.findAllBySopModuleIdOrderByCreatedAtDescIdDesc(module.getId())
                    .stream().map(e -> e.getDetail()).toList());
        }
    }

    @Test
    @DisplayName("Rewriting a module and its quiz: the stored SOP text is masked for the AI and stays out of the logs")
    void rewrites(CapturedOutput output) {
        generation.generateForDocument(docId);
        SopModule module = savedModules().get(0);
        gemini.bodies.clear();

        generation.regenerateModule(module, "Shorter please");
        generation.regenerateQuiz(modules.findById(module.getId()).orElseThrow(), "Ask about the float");

        assertEquals(2, gemini.bodies.size());
        assertNothingPersonalWasSent();
        assertNothingPersonalWasLogged(output);
    }

    @Test
    @DisplayName("Reading an uploaded file: the text it returns stays out of the logs")
    void parsing(CapturedOutput output) {
        generation.clearGenerated(docId); // any bean call, so the aspect has printed something

        String text = parser.extractText(new ByteArrayInputStream(SOP_TEXT.getBytes(StandardCharsets.UTF_8)), "sop.txt");

        assertTrue(text.contains("900101-14-5678"), "the parser itself returns the text unchanged");
        assertNothingPersonalWasLogged(output);
    }
}
