package com.tbm.careerpathlearning.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Thin client over the Google Gemini generateContent REST API.
 * Returns the model's raw text output (we request application/json output and
 * parse it downstream).
 */
@Service
public class GeminiClient {

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model:gemini-3.1-flash-lite}")
    private String model;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final int MAX_ATTEMPTS = 4;

    /** Added to a prompt only when personal data in it is hidden behind labels (FR-06-17). */
    private static final String MASKED_NOTE = "\n\nNote: personal details in this text are hidden behind labels in square brackets,"
            + " such as [IC_NUMBER] or [NAME]. Keep a label exactly as written if you need it, never guess what it hides,"
            + " and do not write quiz questions about it.";

    private final WebClient webClient = WebClient.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
            .build();

    /**
     * Sends the prompt to Gemini asking for a JSON response and returns the raw
     * JSON text from the first candidate. Retries with backoff on transient
     * 429/503 responses. Personal data is masked before the first attempt, so a
     * retry sends the same masked text.
     */
    public String generateJson(String prompt) {
        String safePrompt = withPersonalDataMasked(prompt);
        WebClientResponseException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return callOnce(safePrompt);
            } catch (WebClientResponseException e) {
                int code = e.getStatusCode().value();
                boolean retryable = (code == 429 || code == 503);
                last = e;
                if (retryable && attempt < MAX_ATTEMPTS) {
                    // Free-tier limit is per-minute, so wait long enough to clear the window.
                    long waitSeconds = 30L * attempt;
                    log.warn("Gemini {} (attempt {}/{}), retrying in {}s", code, attempt, MAX_ATTEMPTS, waitSeconds);
                    try {
                        Thread.sleep(waitSeconds * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                break;
            }
        }
        String detail = last == null ? "unknown error" : (last.getStatusCode().value() + " " + last.getResponseBodyAsString());
        throw new RuntimeException("Gemini request failed: " + detail);
    }

    /** The prompt as Gemini may see it. If the filter fails this throws, so nothing is sent (FR-06-17). */
    private String withPersonalDataMasked(String prompt) {
        SensitiveDataFilter.Result masked = SensitiveDataFilter.mask(prompt);
        if (masked.total() > 0) {
            log.info("Masked personal data in an AI prompt: {}", masked.counts()); // counts only, never the values
        }
        return SensitiveDataFilter.hasLabels(masked.text()) ? masked.text() + MASKED_NOTE : masked.text();
    }

    private String callOnce(String prompt) {
        String url = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.4
                )
        );

        JsonNode response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(120))
                .block();

        if (response == null) {
            throw new RuntimeException("Gemini returned an empty response.");
        }

        JsonNode promptFeedback = response.path("promptFeedback").path("blockReason");
        if (!promptFeedback.isMissingNode()) {
            throw new RuntimeException("Gemini blocked the request: " + promptFeedback.asText());
        }

        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            throw new RuntimeException("Gemini response had no content: " + response);
        }

        return parts.get(0).path("text").asText();
    }
}
