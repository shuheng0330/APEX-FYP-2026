package com.tbm.careerpathlearning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Stands in for Google Gemini on this computer, so a test can read exactly what would leave APEX: the real
 * {@link GeminiClient} sends real HTTP requests here.
 */
public final class FakeGeminiServer implements AutoCloseable {

    /** A {@link GeminiClient} that also keeps each prompt it was given, before anything was masked. */
    public static final class RecordingClient extends GeminiClient {
        public final List<String> given = new CopyOnWriteArrayList<>();

        @Override
        public String generateJson(String prompt) {
            given.add(prompt);
            return super.generateJson(prompt);
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpServer server;
    /** Each request's whole body, as it crossed the wire. */
    public final List<String> bodies = new CopyOnWriteArrayList<>();
    /** The prompt text inside each request. */
    public final List<String> prompts = new CopyOnWriteArrayList<>();
    /** The JSON text to answer a prompt with; null answers 400, which the client doesn't retry. */
    public volatile Function<String, String> answer = prompt -> "{}";
    /** The status of the next answers, when a test wants something other than the script. */
    public volatile int statusOverride;

    public FakeGeminiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            String prompt = JSON.readTree(body).path("contents").path(0).path("parts").path(0).path("text").asText();
            prompts.add(prompt);
            String text = answer.apply(prompt);
            int status = statusOverride != 0 ? statusOverride : text == null ? 400 : 200;
            byte[] reply = (status == 200
                    ? JSON.writeValueAsString(Map.of("candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", text)))))))
                    : "{\"error\":{\"code\":" + status + ",\"message\":\"unavailable\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, reply.length);
            exchange.getResponseBody().write(reply);
            exchange.close();
        });
        server.start();
    }

    /** The address to point a {@link GeminiClient} at. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** A real client pointed at this server. */
    public RecordingClient client() {
        RecordingClient client = new RecordingClient();
        ReflectionTestUtils.setField(client, "apiKey", "test-key");
        ReflectionTestUtils.setField(client, "model", "test-model");
        ReflectionTestUtils.setField(client, "baseUrl", baseUrl());
        return client;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
