package com.studyplatform.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.studyplatform.exception.ApiException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Component
@Slf4j
public class ClaudeClient {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    // Generation (esp. study guides) can legitimately take a while; allow a
    // generous read timeout but a tight connect timeout so dead connections fail fast.
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 180_000;
    private static final int MAX_ATTEMPTS = 3;

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public ClaudeClient(
            @Value("${app.claude.api-key}") String apiKey,
            @Value("${app.claude.model}") String model,
            @Value("${app.claude.max-tokens}") int maxTokens) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restTemplate = new RestTemplate(factory);
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    /**
     * Send a message to Claude and get the text response.
     *
     * @param systemPrompt The system instruction (role, constraints, format)
     * @param userMessage  The user's input
     * @return Claude's text response
     */
    public String chat(String systemPrompt, String userMessage) {
        return chat(systemPrompt, userMessage, maxTokens);
    }

    /**
     * Send a message with custom max tokens.
     */
    public String chat(String systemPrompt, String userMessage, int tokens) {
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.badRequest(
                    "Claude API key is not configured. Set app.claude.api-key in application.properties");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", API_VERSION);

        ClaudeRequest request = new ClaudeRequest();
        request.setModel(model);
        request.setMaxTokens(tokens);
        request.setSystem(systemPrompt);
        request.setMessages(List.of(new Message("user", userMessage)));

        HttpEntity<ClaudeRequest> entity = new HttpEntity<>(request, headers);

        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                log.info("Calling Claude API — model: {}, tokens: {}, prompt length: {} chars (attempt {}/{})",
                        model, tokens, userMessage.length(), attempt, MAX_ATTEMPTS);

                ResponseEntity<ClaudeResponse> response = restTemplate.exchange(
                        API_URL, HttpMethod.POST, entity, ClaudeResponse.class);

                if (response.getBody() == null || response.getBody().getContent() == null
                        || response.getBody().getContent().isEmpty()) {
                    throw ApiException.badRequest("Empty response from Claude API");
                }

                String text = response.getBody().getContent().get(0).getText();
                log.info("Claude response received — {} chars", text.length());
                return text;

            } catch (ResourceAccessException e) {
                // Transient I/O problem (connect/read timeout, dropped connection) — retry.
                lastError = e;
                log.warn("Claude API attempt {}/{} failed (I/O): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep(1500L * attempt);
                }
            } catch (RestClientException e) {
                // Non-transient (4xx/5xx from the API) — don't retry.
                log.error("Claude API call failed: {}", e.getMessage());
                throw ApiException.badRequest("AI service unavailable. Please try again later.");
            }
        }

        log.error("Claude API call failed after {} attempts: {}",
                MAX_ATTEMPTS, lastError != null ? lastError.getMessage() : "unknown");
        throw ApiException.badRequest(
                "AI service is temporarily unreachable. Please check your connection and try again.");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ── Request/Response DTOs ─────────────────────────────────

    @Data
    static class ClaudeRequest {
        private String model;
        @JsonProperty("max_tokens")
        private int maxTokens;
        private String system;
        private List<Message> messages;
    }

    @Data
    static class Message {
        private final String role;
        private final String content;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ClaudeResponse {
        private String id;
        private String model;
        private String role;
        private List<ContentBlock> content;
        @JsonProperty("stop_reason")
        private String stopReason;
        private Usage usage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ContentBlock {
        private String type;
        private String text;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Usage {
        @JsonProperty("input_tokens")
        private int inputTokens;
        @JsonProperty("output_tokens")
        private int outputTokens;
    }
}
