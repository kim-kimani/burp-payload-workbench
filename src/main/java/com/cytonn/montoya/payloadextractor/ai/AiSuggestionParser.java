package com.cytonn.montoya.payloadextractor.ai;

import com.cytonn.montoya.payloadextractor.util.JsonNode;

import java.util.ArrayList;
import java.util.List;

/** Parses a DeepSeek chat-completion HTTP response body into a list of suggested payload value strings. */
public final class AiSuggestionParser {

    private AiSuggestionParser() {
    }

    public static List<String> parse(String rawHttpResponseBody) {
        List<String> out = new ArrayList<>();
        if (rawHttpResponseBody == null || rawHttpResponseBody.isBlank()) {
            return out;
        }
        String content;
        try {
            JsonNode root = JsonNode.parse(rawHttpResponseBody);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                return out;
            }
            JsonNode message = choices.get(0).get("message");
            content = message != null ? message.get("content").asString() : null;
        } catch (Exception e) {
            return out;
        }
        if (content == null || content.isBlank()) {
            return out;
        }
        return parseContentAsSuggestions(content);
    }

    /** Extracts the assistant's raw reply text (no suggestion-array parsing) - for the free-form chat flow. */
    public static String extractRawContent(String rawHttpResponseBody) {
        if (rawHttpResponseBody == null || rawHttpResponseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = JsonNode.parse(rawHttpResponseBody);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                return rawHttpResponseBody;
            }
            JsonNode message = choices.get(0).get("message");
            String content = message != null && message.get("content") != null ? message.get("content").asString() : null;
            return content == null ? "" : content;
        } catch (Exception e) {
            return rawHttpResponseBody;
        }
    }

    /** Handles both a clean JSON array and a model that wrapped it in prose/markdown fences despite instructions. */
    public static List<String> parseContentAsSuggestions(String content) {
        List<String> out = new ArrayList<>();
        String trimmed = content.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            String jsonArray = trimmed.substring(start, end + 1);
            try {
                JsonNode arr = JsonNode.parse(jsonArray);
                if (arr.isArray()) {
                    for (int i = 0; i < arr.size(); i++) {
                        JsonNode el = arr.get(i);
                        if (el != null && !el.isNull()) {
                            out.add(el.asString());
                        }
                    }
                    return out;
                }
            } catch (Exception ignored) {
                // fall through to line-based parsing
            }
        }
        for (String line : trimmed.split("\\r?\\n")) {
            String cleaned = line.replaceFirst("^[\\-*\\d.)\\s]+", "").trim();
            if (!cleaned.isEmpty()) {
                out.add(cleaned);
            }
        }
        return out;
    }
}
