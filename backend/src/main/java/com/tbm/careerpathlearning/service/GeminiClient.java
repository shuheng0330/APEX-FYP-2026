package com.tbm.careerpathlearning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tbm.careerpathlearning.aspect.MetadataOnlyLogging;
import com.tbm.careerpathlearning.service.appraisalinsight.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin client over the Google Gemini generateContent REST API.
 * Returns the model's raw text output (we request application/json output and
 * parse it downstream).
 */
@Service
@MetadataOnlyLogging
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

    private final WebClient webClient;
    @Autowired private GeminiDispatchGate coordination;

    public GeminiClient() {
        this(WebClient.builder().codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024)).build());
    }

    GeminiClient(WebClient webClient) { this.webClient = webClient; }

    public String appraisalModel() { return model; }

    /** Full outbound JSON bytes plus fixed protocol headroom, not a character/4 token guess. */
    public long appraisalTokenReservation(String prompt) { return tokenReservation(appraisalBody(prompt)); }

    private long tokenReservation(Map<String,Object> body) {
        try { return Math.addExact(new ObjectMapper().writeValueAsBytes(body).length,1024L); }
        catch (Exception invalid) { throw new AppraisalProviderException("NOT_CONFIGURED"); }
    }
    private Map<String,Object> appraisalBody(String prompt) {
        return Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json", "temperature", 0.2,
                        "maxOutputTokens", 4096, "responseSchema", appraisalResponseSchema()));
    }

    public void validateAppraisalConfiguration() {
        if (apiKey == null || apiKey.isBlank() || model == null || !model.matches("[A-Za-z0-9._-]{1,100}"))
            throw new AppraisalProviderException("NOT_CONFIGURED");
    }

    /** One bounded attempt, with no provider content or credentials in an exception. */
    public String generateAppraisalJson(String prompt) {
        validateAppraisalConfiguration();
        var body=appraisalBody(prompt);
        GeminiDispatchGate.Permit permit=null;boolean responseKnown=false,dispatched=false;Long inputTokens=null;
        try {
            if (coordination!=null) {
                var context=ProviderDispatchContext.current();
                if (context==null) throw new AppraisalProviderException("NOT_CONFIGURED");
                permit=context.permit();coordination.begin(permit,tokenReservation(body),context::dispatched);
                dispatched=true;
            }
            JsonNode response = webClient.post()
                    .uri(baseUrl + "/models/" + model + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve().bodyToMono(JsonNode.class).timeout(Duration.ofSeconds(60)).block();
            responseKnown=true;inputTokens=inputTokens(response);
            if (response == null || response.path("promptFeedback").has("blockReason"))
                throw new AppraisalProviderException("BLOCKED_OR_EMPTY");
            var candidate = response.path("candidates").path(0);
            if (!"STOP".equals(candidate.path("finishReason").asText()))
                throw new AppraisalProviderException("INCOMPLETE_RESPONSE");
            var parts = candidate.path("content").path("parts");
            if (!parts.isArray()) throw new AppraisalProviderException("BLOCKED_OR_EMPTY");
            var output = new StringBuilder();
            for (var part : parts) {
                if (!part.path("thought").asBoolean(false) && part.path("text").isTextual())
                    output.append(part.path("text").asText());
            }
            if (output.isEmpty() || output.length() > 30000) throw new AppraisalProviderException("INVALID_RESPONSE");
            return output.toString();
        } catch (AppraisalProviderException safe) {
            if (dispatched) coordination.providerFailure();
            throw safe;
        } catch (WebClientResponseException failure) {
            if (dispatched) coordination.providerFailure();
            responseKnown=true;
            var safe=new AppraisalProviderException(failure.getStatusCode().value() == 429 ? "RATE_LIMITED" : "PROVIDER_UNAVAILABLE",
                    failure.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
            if (coordination!=null && failure.getStatusCode().value()==429) coordination.providerRateLimited(model,safe);
            throw safe;
        } catch (RuntimeException failure) {
            if (dispatched) coordination.providerFailure();
            throw new AppraisalProviderException("PROVIDER_UNAVAILABLE");
        } finally {
            if (coordination!=null && permit!=null) coordination.finishPermit(permit,inputTokens,responseKnown);
        }
    }

    private Long inputTokens(JsonNode response) {
        var count=response==null?null:response.path("usageMetadata").path("promptTokenCount");
        return count!=null && count.isIntegralNumber() && count.canConvertToLong() && count.longValue()>=0?count.longValue():null;
    }

    private static Map<String, Object> appraisalResponseSchema() {
        var statement = Map.of("type", "OBJECT", "properties", Map.of(
                "text", Map.of("type", "STRING"),
                "evidenceIds", Map.of("type", "ARRAY", "items", Map.of("type", "STRING"),
                        "minItems", "1", "maxItems", "10")),
                "required", List.of("text", "evidenceIds"), "propertyOrdering", List.of("text", "evidenceIds"));
        var list = Map.of("type", "ARRAY", "items", statement, "minItems", "1", "maxItems", "3");
        var fields = new LinkedHashMap<String, Object>();
        for (String name : List.of("performanceSummary", "keyStrengths", "areasForImprovement", "performanceTrend"))
            fields.put(name, list);
        fields.put("appraisalConsiderations", Map.of("type", "OBJECT", "properties", Map.of(
                "promotionDiscussion", statement, "salaryIncrementDiscussion", statement),
                "required", List.of("promotionDiscussion", "salaryIncrementDiscussion"),
                "propertyOrdering", List.of("promotionDiscussion", "salaryIncrementDiscussion")));
        fields.put("informationGaps", list);
        var order = List.copyOf(fields.keySet());
        return Map.of("type", "OBJECT", "properties", fields, "required", order, "propertyOrdering", order);
    }

    public static class AppraisalProviderException extends RuntimeException {
        private final String code;
        private final String retryAfter;

        public AppraisalProviderException(String code) { this(code, null); }

        public AppraisalProviderException(String code, String retryAfter) {
            super("AI insight could not be generated: " + code);
            this.code = code;
            this.retryAfter = safeRetryAfter(retryAfter);
        }

        public String code() { return code; }

        /** Returns a future rate-limit retry time, or null for other failures. Never retries the call. */
        public OffsetDateTime retryAt(OffsetDateTime now) {
            if (!"RATE_LIMITED".equals(code)) return null;
            if (retryAfter != null) {
                try {
                    var retry = retryAfter.matches("[0-9]+")
                            ? now.plusSeconds(Long.parseLong(retryAfter))
                            : OffsetDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME)
                                    .withOffsetSameInstant(now.getOffset());
                    if (retry.isAfter(now) || "0".equals(retryAfter)) return retry;
                } catch (DateTimeException | NumberFormatException invalid) {
                    // Missing, stale, malformed or unrepresentable hints use the bounded fallback.
                }
            }
            return now.plusSeconds(60);
        }

        private static String safeRetryAfter(String value) {
            if (value == null || value.length() > 100 || value.chars().anyMatch(c -> c < 0x20 || c > 0x7e))
                return null;
            var trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }

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
                        waitBeforeSopRetry(waitSeconds);
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

    protected void waitBeforeSopRetry(long seconds) throws InterruptedException { Thread.sleep(seconds*1000); }

    private String callOnce(String prompt) {
        String url = baseUrl + "/models/" + model + ":generateContent";

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.4
                )
        );

        GeminiDispatchGate.Permit permit=null;boolean responseKnown=false,dispatched=false;Long inputTokens=null;
        try {
        if (coordination!=null && coordination.enabled()) {
            permit=coordination.awaitSopPermit(model,tokenReservation(body));coordination.begin(permit,tokenReservation(body),()->{});
            dispatched=true;
        }
        JsonNode response = webClient.post()
                .uri(url)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofSeconds(120))
                .block();
        responseKnown=true;inputTokens=inputTokens(response);

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
        } catch (WebClientResponseException failure) {
            if (dispatched) coordination.providerFailure();
            responseKnown=true;
            if (coordination!=null && coordination.enabled() && failure.getStatusCode().value()==429)
                coordination.providerRateLimited(model,new AppraisalProviderException("RATE_LIMITED",failure.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)));
            throw failure;
        } catch (RuntimeException failure) {
            if (dispatched) coordination.providerFailure();
            throw failure;
        } finally {
            if (coordination!=null && permit!=null) coordination.finishPermit(permit,inputTokens,responseKnown);
        }
    }
}
