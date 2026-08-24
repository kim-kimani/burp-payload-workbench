package com.cytonn.montoya.payloadextractor.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Talks to the DeepSeek chat-completions API over a plain {@code java.net.http.HttpClient} - this
 * is a call the extension itself makes to an external AI service, deliberately independent of
 * Burp's own request-sending machinery (Montoya's {@code Http}, which is for target traffic).
 */
public final class DeepSeekClient {

    private final HttpClient httpClient;

    public DeepSeekClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public List<String> suggestValues(AiSettings settings, String fieldName, String category, String currentValue, int howMany) throws IOException {
        if (!settings.isConfigured()) {
            throw new IllegalStateException("AI suggestions are not configured - set an API key in Settings first.");
        }
        String body = AiRequestBuilder.buildSuggestionRequest(settings, fieldName, category, currentValue, howMany);
        String responseBody = post(settings, body);
        return AiSuggestionParser.parse(responseBody);
    }

    /**
     * Sends an arbitrary chat-completion request built by {@link AiRequestBuilder#buildFreeformRequest}
     * (full request/response context + a user-supplied prompt) and returns the raw assistant reply
     * text, for the AI Assistant tab's free-form "Send to DeepSeek" flow.
     */
    public String chat(AiSettings settings, String systemPrompt, String userPrompt) throws IOException {
        if (!settings.isConfigured()) {
            throw new IllegalStateException("AI suggestions are not configured - set an API key in Settings first.");
        }
        String body = AiRequestBuilder.buildFreeformRequest(settings, systemPrompt, userPrompt);
        String responseBody = post(settings, body);
        return AiSuggestionParser.extractRawContent(responseBody);
    }

    /** Fires a minimal request against the configured endpoint purely to validate connectivity + credentials. */
    public String testConnection(AiSettings settings) throws IOException {
        if (settings.apiKey() == null || settings.apiKey().isBlank()) {
            throw new IllegalStateException("No API key set.");
        }
        String body = AiRequestBuilder.buildFreeformRequest(settings, "You are a connectivity check.", "Reply with the single word: OK");
        String responseBody = post(settings, body);
        return AiSuggestionParser.extractRawContent(responseBody);
    }

    private String post(AiSettings settings, String body) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(settings.endpoint()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + settings.apiKey())
                .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("DeepSeek API returned HTTP " + response.statusCode() + ": " + truncate(response.body()));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling DeepSeek API", e);
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
