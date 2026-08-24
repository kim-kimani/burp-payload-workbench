package com.cytonn.montoya.payloadextractor.db;

import com.cytonn.montoya.payloadextractor.util.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A simple host include/exclude filter so the passive listener and the auto-detect pipeline only
 * pay attention to traffic the analyst actually cares about. Patterns are plain wildcard globs
 * ({@code *} and {@code ?}), matched case-insensitively against the request's host; an empty
 * include list means "everything is included" (exclude patterns still apply on top).
 */
public final class ScopeFilter {

    private final List<String> includeHostPatterns = new ArrayList<>();
    private final List<String> excludeHostPatterns = new ArrayList<>();
    private boolean enabled = false;
    private boolean passiveLearningEnabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /**
     * Master switch for automatic ("passive") learning of values into Payload Collections - from
     * traffic seen by {@code PassiveTrafficListener} and from auto-remember-on-load in the
     * Workbench. Distinct from {@link #isEnabled()}/the include-exclude glob patterns above, which
     * scope *which hosts* are eligible once passive learning is on. Defaults to on.
     */
    public boolean isPassiveLearningEnabled() { return passiveLearningEnabled; }
    public void setPassiveLearningEnabled(boolean passiveLearningEnabled) { this.passiveLearningEnabled = passiveLearningEnabled; }

    public List<String> includeHostPatterns() { return includeHostPatterns; }
    public List<String> excludeHostPatterns() { return excludeHostPatterns; }

    public void addInclude(String pattern) { if (pattern != null && !pattern.isBlank()) includeHostPatterns.add(pattern.trim()); }
    public void addExclude(String pattern) { if (pattern != null && !pattern.isBlank()) excludeHostPatterns.add(pattern.trim()); }
    public void removeInclude(String pattern) { includeHostPatterns.remove(pattern); }
    public void removeExclude(String pattern) { excludeHostPatterns.remove(pattern); }

    public boolean isInScope(String host) {
        if (!enabled) {
            return true;
        }
        if (host == null) {
            return false;
        }
        for (String excl : excludeHostPatterns) {
            if (globMatch(excl, host)) {
                return false;
            }
        }
        if (includeHostPatterns.isEmpty()) {
            return true;
        }
        for (String incl : includeHostPatterns) {
            if (globMatch(incl, host)) {
                return true;
            }
        }
        return false;
    }

    private static boolean globMatch(String glob, String value) {
        try {
            String regex = "(?i)" + Pattern.quote(glob).replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q");
            return Pattern.matches(regex, value);
        } catch (PatternSyntaxException e) {
            return glob.equalsIgnoreCase(value);
        }
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"enabled\":").append(enabled)
          .append(",\"passiveLearningEnabled\":").append(passiveLearningEnabled)
          .append(",\"include\":").append(listJson(includeHostPatterns))
          .append(",\"exclude\":").append(listJson(excludeHostPatterns))
          .append('}');
        return sb.toString();
    }

    private static String listJson(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(items.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    public static ScopeFilter fromJson(String json) {
        ScopeFilter filter = new ScopeFilter();
        if (json == null || json.isBlank()) {
            return filter;
        }
        try {
            JsonNode root = JsonNode.parse(json);
            JsonNode enabledNode = root.get("enabled");
            if (enabledNode != null) {
                filter.enabled = Boolean.parseBoolean(enabledNode.asString());
            }
            JsonNode passiveNode = root.get("passiveLearningEnabled");
            if (passiveNode != null) {
                filter.passiveLearningEnabled = Boolean.parseBoolean(passiveNode.asString());
            }
            JsonNode include = root.get("include");
            if (include != null && include.isArray()) {
                for (int i = 0; i < include.size(); i++) filter.addInclude(include.get(i).asString());
            }
            JsonNode exclude = root.get("exclude");
            if (exclude != null && exclude.isArray()) {
                for (int i = 0; i < exclude.size(); i++) filter.addExclude(exclude.get(i).asString());
            }
        } catch (Exception ignored) {
        }
        return filter;
    }
}
