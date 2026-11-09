package com.tbm.careerpathlearning.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeminiAppraisalClientTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-06T12:00:00+08:00");

    @Test void providerSchemaMatchesAllSixSectionsAndExistingListLimits() throws Exception {
        var captured = new AtomicReference<ClientRequest>();
        var client = client(request -> { captured.set(request); return Mono.just(response(200,
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}")); });
        client.generateAppraisalJson("fictional evidence");
        var wire = new org.springframework.mock.http.client.reactive.MockClientHttpRequest(HttpMethod.POST, captured.get().url());
        captured.get().writeTo(wire, ExchangeStrategies.withDefaults()).block();
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(wire.getBodyAsString().block());
        var schema = body.path("generationConfig").path("responseSchema");
        assertEquals("OBJECT", schema.path("type").asText());
        assertEquals(6, schema.path("required").size());
        for (String name : java.util.List.of("performanceSummary", "keyStrengths", "areasForImprovement", "performanceTrend", "informationGaps")) {
            var list = schema.path("properties").path(name);
            assertEquals("ARRAY", list.path("type").asText());
            assertEquals("1", list.path("minItems").asText()); assertEquals("3", list.path("maxItems").asText());
            assertEquals(2, list.path("items").path("required").size());
            assertEquals("10", list.path("items").path("properties").path("evidenceIds").path("maxItems").asText());
        }
        assertEquals(2, schema.path("properties").path("appraisalConsiderations").path("required").size());
    }
    @Test void usesHeaderAuthenticationAndExactlyOneBoundedRequest() {
        var calls = new AtomicInteger(); var request = new AtomicReference<ClientRequest>();
        var client = client(r -> {
            request.set(r); calls.incrementAndGet();
            return Mono.just(response(200, "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{}\"}]}}]}"));
        });
        assertEquals("{}", client.generateAppraisalJson("fictional evidence"));
        assertEquals(1, calls.get()); assertFalse(request.get().url().toString().contains("secret"));
        assertNull(request.get().url().getQuery()); assertEquals("secret-test-key", request.get().headers().getFirst("x-goog-api-key"));
        assertEquals("gemini-test", client.appraisalModel());
    }
    @Test void rateLimitAndServerErrorsDoNotRetryOrExposeProviderBodiesOrKeys() {
        for (int status : new int[]{429, 503, 401}) {
            var calls = new AtomicInteger();
            var client = client(r -> { calls.incrementAndGet(); return Mono.just(response(status, "private-provider-body-secret-test-key")); });
            var failure = assertThrows(GeminiClient.AppraisalProviderException.class, () -> client.generateAppraisalJson("private prompt"));
            assertEquals(status == 429 ? "RATE_LIMITED" : "PROVIDER_UNAVAILABLE", failure.code());
            assertEquals(1, calls.get()); assertNull(failure.getCause());
            assertFalse(failure.toString().contains("private")); assertFalse(failure.toString().contains("secret-test-key"));
        }
    }
    @Test void rateLimitUsesRetryAfterSecondsWithoutRetrying() {
        var calls = new AtomicInteger();
        var client = client(r -> {
            calls.incrementAndGet();
            return Mono.just(rateLimited("120"));
        });
        var failure = assertThrows(GeminiClient.AppraisalProviderException.class,
                () -> client.generateAppraisalJson("fictional"));
        assertEquals(NOW.plusSeconds(120), failure.retryAt(NOW));
        assertEquals(NOW.plusSeconds(120), failure.retryAt(NOW));
        assertEquals(1, calls.get());
        assertNull(failure.getCause());
    }
    @Test void rateLimitUsesFutureHttpDateRelativeToTheSuppliedClock() {
        var client = client(r -> Mono.just(rateLimited("Fri, 06 Nov 2026 04:02:00 GMT")));
        var failure = assertThrows(GeminiClient.AppraisalProviderException.class,
                () -> client.generateAppraisalJson("fictional"));
        assertEquals(NOW.plusMinutes(2), failure.retryAt(NOW));
        assertEquals(NOW.plusMinutes(4), failure.retryAt(NOW.plusMinutes(3)));
    }
    @Test void invalidMissingPastAndUnsafeRetryAfterValuesUseSixtySecondFallback() {
        for (String header : new String[]{null, "", " ", "not-a-date", "-1", "+1", "1.5",
                "9223372036854775807", "9999999999999999999999999999",
                "Thu, 05 Nov 2026 04:00:00 GMT", "Fri, 06 Nov 2026 04:00:00 GMT",
                "120\r\nprivate-header", "120\t", "120\u007f", "120\u0080",
                " ".repeat(101) + "120"}) {
            var client = client(r -> Mono.just(rateLimited(header)));
            var failure = assertThrows(GeminiClient.AppraisalProviderException.class,
                    () -> client.generateAppraisalJson("fictional"));
            assertEquals(NOW.plusSeconds(60), failure.retryAt(NOW), "Invalid Retry-After must use fallback");
            assertFalse(failure.toString().contains("private"));
            assertNull(failure.getCause());
        }
    }
    @Test void retryAfterHeaderIsTrimmedButNeverIncludedInExceptionText() {
        assertEquals(NOW,new GeminiClient.AppraisalProviderException("RATE_LIMITED","0").retryAt(NOW));
        assertEquals(NOW.plusSeconds(5), new GeminiClient.AppraisalProviderException("RATE_LIMITED", " 5 ").retryAt(NOW));
        var failure = new GeminiClient.AppraisalProviderException("RATE_LIMITED", "private-header-value");
        assertFalse(failure.getMessage().contains("private-header-value"));
        assertFalse(failure.toString().contains("private-header-value"));
        assertNull(failure.getCause());
    }
    @Test void legacyExceptionConstructorKeepsFallbackAndNonRateLimitsDoNotScheduleRetries() {
        assertEquals(NOW.plusSeconds(60), new GeminiClient.AppraisalProviderException("RATE_LIMITED").retryAt(NOW));
        assertNull(new GeminiClient.AppraisalProviderException("PROVIDER_UNAVAILABLE").retryAt(NOW));
        assertNull(new GeminiClient.AppraisalProviderException("PROVIDER_UNAVAILABLE", "120").retryAt(NOW));
        var client = client(r -> Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "120").body("private-provider-body").build()));
        assertNull(assertThrows(GeminiClient.AppraisalProviderException.class,
                () -> client.generateAppraisalJson("fictional")).retryAt(NOW));
    }
    @Test void timeoutFailuresAreSanitisedWithoutRetryOrAutomaticRetryTime() {
        var calls = new AtomicInteger();
        var client = client(r -> {
            calls.incrementAndGet();
            return Mono.error(new TimeoutException("private timeout secret-test-key"));
        });
        var failure = assertThrows(GeminiClient.AppraisalProviderException.class,
                () -> client.generateAppraisalJson("private prompt"));
        assertEquals("PROVIDER_UNAVAILABLE", failure.code());
        assertNull(failure.retryAt(NOW));
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains("private"));
        assertFalse(failure.toString().contains("secret-test-key"));
        assertEquals(1, calls.get());
    }
    @Test @org.junit.jupiter.api.Timeout(10)
    void appraisalTimeoutCancelsTheOnlyRequestAfterSixtySeconds() {
        var timeoutTask = new AtomicReference<Runnable>();
        var scheduledDelay = new AtomicReference<Duration>();
        var cancelled = new AtomicBoolean();
        var calls = new AtomicInteger();
        var timer = mock(Scheduler.class);
        when(timer.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(invocation -> {
            timeoutTask.set(invocation.getArgument(0));
            scheduledDelay.set(Duration.ofNanos(invocation.<TimeUnit>getArgument(2)
                    .toNanos(invocation.getArgument(1))));
            return (Disposable) () -> {};
        });
        var client = client(r -> {
            calls.incrementAndGet();
            return Mono.create(sink -> {
                sink.onCancel(() -> cancelled.set(true));
                assertNotNull(timeoutTask.get());
                timeoutTask.get().run();
            });
        });

        try (var schedulers = mockStatic(Schedulers.class, CALLS_REAL_METHODS)) {
            schedulers.when(Schedulers::parallel).thenReturn(timer);
            var failure = assertThrows(GeminiClient.AppraisalProviderException.class,
                    () -> client.generateAppraisalJson("private prompt"));
            assertEquals("PROVIDER_UNAVAILABLE", failure.code());
            assertNull(failure.getCause());
            assertNull(failure.retryAt(NOW));
            assertFalse(failure.toString().contains("private"));
        }
        assertEquals(Duration.ofSeconds(60), scheduledDelay.get());
        assertEquals(1, calls.get());
        assertTrue(cancelled.get());
    }
    @Test void rejectsBlockedEmptyAndTruncatedResponsesWithoutReturningThem() {
        for (String body : new String[]{"{}", "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}",
                "{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\",\"content\":{\"parts\":[{\"text\":\"private partial\"}]}}]}"}) {
            var failure = assertThrows(GeminiClient.AppraisalProviderException.class,
                    () -> client(r -> Mono.just(response(200, body))).generateAppraisalJson("fictional"));
            assertFalse(failure.getMessage().contains("private"));
        }
    }
    @Test void ignoresThoughtPartsAndJoinsVisibleTextParts() {
        var client = client(r -> Mono.just(response(200,
                "{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private reasoning\"},{\"text\":\"{\"},{\"text\":\"}\"}]}}]}")));
        assertEquals("{}", client.generateAppraisalJson("fictional"));
    }
    @Test void networkExceptionsAndMissingConfigurationAreSanitised() {
        var client = client(r -> Mono.error(new RuntimeException("secret-test-key private provider failure")));
        assertEquals("PROVIDER_UNAVAILABLE", assertThrows(GeminiClient.AppraisalProviderException.class,
                () -> client.generateAppraisalJson("fictional")).code());
        ReflectionTestUtils.setField(client, "apiKey", "");
        assertEquals("NOT_CONFIGURED", assertThrows(GeminiClient.AppraisalProviderException.class,
                () -> client.generateAppraisalJson("fictional")).code());
    }
    @Test void legacySopCallsStillUseTheirExistingJsonResponseContract() {
        var client = client(r -> Mono.just(response(200,
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"legacy-json\"}]}}]}")));
        assertEquals("legacy-json", client.generateJson("SOP example"));
    }
    private GeminiClient client(ExchangeFunction exchange) {
        var client = new GeminiClient(WebClient.builder().exchangeFunction(exchange).build());
        ReflectionTestUtils.setField(client, "apiKey", "secret-test-key");
        ReflectionTestUtils.setField(client, "model", "gemini-test");
        ReflectionTestUtils.setField(client, "baseUrl", "https://provider.invalid/v1beta");
        return client;
    }
    private ClientResponse response(int status, String body) {
        return ClientResponse.create(HttpStatusCode.valueOf(status)).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(body).build();
    }
    private ClientResponse rateLimited(String retryAfter) {
        var response = ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("private-provider-body-secret-test-key");
        if (retryAfter != null) response.header(HttpHeaders.RETRY_AFTER, retryAfter);
        return response.build();
    }
}
