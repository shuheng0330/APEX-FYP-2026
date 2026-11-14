package com.tbm.careerpathlearning.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/** The last point before Gemini: what actually crosses the wire (FR-06-17). */
class GeminiClientMaskingTest {

    private static final String NOTE_START = "Note: personal details in this text are hidden";

    private FakeGeminiServer gemini;
    private GeminiClient client;

    @BeforeEach
    void setUp() throws IOException {
        gemini = new FakeGeminiServer();
        client = gemini.client();
    }

    @AfterEach
    void tearDown() {
        gemini.close();
    }

    @Test
    @DisplayName("Personal data in a prompt never crosses the wire, and Gemini is told what the labels are")
    void personalDataIsMasked() {
        String prompt = "Rewrite this.\nName: Ahmad bin Ali\nIC No: 900101-14-5678\nPassport No: A12345678\nCall 012-345 6789.\n"
                + "Mail someone@example.com.\nAccount No: 1234567890\nCount the float.";

        assertEquals("{}", client.generateJson(prompt));

        assertEquals(1, gemini.bodies.size());
        for (String value : List.of("Ahmad bin Ali", "900101-14-5678", "A12345678", "012-345 6789", "someone@example.com", "1234567890")) {
            assertFalse(gemini.bodies.get(0).contains(value), value);
        }
        String sent = gemini.prompts.get(0);
        assertTrue(sent.startsWith("Rewrite this.\nName: [NAME]\nIC No: [IC_NUMBER]\nPassport No: [PASSPORT_NUMBER]\nCall [MOBILE_NUMBER].\n"
                + "Mail [EMAIL].\nAccount No: [BANK_ACCOUNT]\nCount the float."), sent);
        assertEquals(1, sent.split(NOTE_START, -1).length - 1, "the note is added once");
    }

    @Test
    @DisplayName("A prompt with nothing to mask is sent exactly as it was, with no note")
    void cleanPromptIsUntouched() {
        String prompt = "Rewrite this.\nCount the float. Report shortages to finance@tbm.net or call 03-7890 1234.";

        client.generateJson(prompt);

        assertEquals(prompt, gemini.prompts.get(0));
    }

    @Test
    @DisplayName("Text masked earlier stays as it is, and Gemini is still told what the labels are")
    void alreadyMaskedPrompt() {
        String prompt = "Rewrite this.\nIC No: [IC_NUMBER]";

        client.generateJson(prompt);

        assertTrue(gemini.prompts.get(0).startsWith(prompt + "\n\n" + NOTE_START), gemini.prompts.get(0));
    }

    @Test
    @DisplayName("A request Gemini refuses still sent only masked text, and the failure is reported as before")
    void refusedRequest() {
        gemini.answer = prompt -> null;

        RuntimeException failure = assertThrows(RuntimeException.class, () -> client.generateJson("IC No: 900101-14-5678"));

        assertTrue(failure.getMessage().startsWith("Gemini request failed: 400"), failure.getMessage());
        assertFalse(failure.getMessage().contains("900101-14-5678"));
        assertEquals(1, gemini.bodies.size());
        assertFalse(gemini.bodies.get(0).contains("900101-14-5678"));
    }

    @Test
    @DisplayName("If the filter fails, nothing is sent")
    void filterFailureSendsNothing() {
        try (MockedStatic<SensitiveDataFilter> filter = mockStatic(SensitiveDataFilter.class)) {
            filter.when(() -> SensitiveDataFilter.mask(any())).thenThrow(new IllegalStateException("could not be checked"));

            assertThrows(IllegalStateException.class, () -> client.generateJson("IC No: 900101-14-5678"));
        }

        assertTrue(gemini.bodies.isEmpty());
    }
}
