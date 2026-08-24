package com.cytonn.montoya.payloadextractor.ai;

/**
 * Builds the JSON chat-completion request body sent to DeepSeek for payload suggestions. The
 * system prompt firmly instructs the model to reply with nothing but a JSON array of strings, so
 * {@link AiSuggestionParser} can parse it reliably.
 */
public final class AiRequestBuilder {

    private AiRequestBuilder() {
    }

    public static String buildSuggestionRequest(AiSettings settings, String fieldName, String category,
                                                  String currentValue, int howMany) {
        String system = "You are a security testing assistant helping a penetration tester generate realistic "
                + "candidate payload values for a specific HTTP request field, for use in authorized security "
                + "testing. Reply with ONLY a JSON array of " + howMany + " short string values - no prose, no "
                + "markdown, no code fences, just a raw JSON array like [\"value1\",\"value2\"].";

        String user = "Field name: " + safe(fieldName) + "\n"
                + "Detected category: " + safe(category) + "\n"
                + "Current/example value: " + safe(currentValue) + "\n"
                + "Suggest " + howMany + " plausible, varied candidate values appropriate for this field's category "
                + "(e.g. edge cases, boundary values, common defaults, or realistic-looking data as appropriate).";

        return "{"
                + "\"model\":" + q(settings.model()) + ","
                + "\"temperature\":" + settings.temperature() + ","
                + "\"max_tokens\":" + settings.maxTokens() + ","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + q(system) + "},"
                + "{\"role\":\"user\",\"content\":" + q(user) + "}"
                + "]}";
    }

    /**
     * Builds a free-form chat-completion request for the AI Assistant tab: a caller-supplied system
     * prompt (usually built by the panel from the full request/response context and focus parameter)
     * plus the analyst's own prompt text as the user message.
     */
    public static String buildFreeformRequest(AiSettings settings, String systemPrompt, String userPrompt) {
        return "{"
                + "\"model\":" + q(settings.model()) + ","
                + "\"temperature\":" + settings.temperature() + ","
                + "\"max_tokens\":" + settings.maxTokens() + ","
                + "\"messages\":["
                + "{\"role\":\"system\",\"content\":" + q(safe(systemPrompt)) + "},"
                + "{\"role\":\"user\",\"content\":" + q(safe(userPrompt)) + "}"
                + "]}";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }
}
