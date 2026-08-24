package com.cytonn.montoya.payloadextractor.ai;

import com.cytonn.montoya.payloadextractor.util.JsonNode;

/** User-configurable settings for the DeepSeek AI suggestion integration. */
public final class AiSettings {

    private boolean enabled = false;
    private String apiKey = "";
    private String endpoint = "https://api.deepseek.com/chat/completions";
    private String model = "deepseek-chat";
    private double temperature = 0.7;
    private int maxTokens = 512;
    private int timeoutSeconds = 60;
    private boolean sendSensitiveByDefault = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String apiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }

    public String endpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = (endpoint == null || endpoint.isBlank()) ? this.endpoint : endpoint; }

    public String model() { return model; }
    public void setModel(String model) { this.model = (model == null || model.isBlank()) ? this.model : model; }

    public double temperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int maxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public int timeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds <= 0 ? 60 : timeoutSeconds; }

    /**
     * Default state of the "include sensitive headers/values" checkbox in the AI Assistant panel.
     * Sending is always opt-in per request regardless of this default - it only pre-ticks the box.
     */
    public boolean isSendSensitiveByDefault() { return sendSensitiveByDefault; }
    public void setSendSensitiveByDefault(boolean sendSensitiveByDefault) { this.sendSensitiveByDefault = sendSensitiveByDefault; }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    public String toJson() {
        return "{\"enabled\":" + enabled
                + ",\"apiKey\":" + q(apiKey)
                + ",\"endpoint\":" + q(endpoint)
                + ",\"model\":" + q(model)
                + ",\"temperature\":" + temperature
                + ",\"maxTokens\":" + maxTokens
                + ",\"timeoutSeconds\":" + timeoutSeconds
                + ",\"sendSensitiveByDefault\":" + sendSensitiveByDefault
                + "}";
    }

    public static AiSettings fromJson(String json) {
        AiSettings settings = new AiSettings();
        if (json == null || json.isBlank()) {
            return settings;
        }
        try {
            JsonNode root = JsonNode.parse(json);
            JsonNode enabledNode = root.get("enabled");
            if (enabledNode != null) settings.enabled = Boolean.parseBoolean(enabledNode.asString());
            if (root.get("apiKey") != null) settings.apiKey = root.get("apiKey").asString();
            if (root.get("endpoint") != null) settings.endpoint = root.get("endpoint").asString();
            if (root.get("model") != null) settings.model = root.get("model").asString();
            if (root.get("temperature") != null) settings.temperature = Double.parseDouble(root.get("temperature").asString());
            if (root.get("maxTokens") != null) settings.maxTokens = Integer.parseInt(root.get("maxTokens").asString());
            if (root.get("timeoutSeconds") != null) settings.timeoutSeconds = Integer.parseInt(root.get("timeoutSeconds").asString());
            if (root.get("sendSensitiveByDefault") != null) settings.sendSensitiveByDefault = Boolean.parseBoolean(root.get("sendSensitiveByDefault").asString());
        } catch (Exception ignored) {
        }
        return settings;
    }

    private static String q(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
