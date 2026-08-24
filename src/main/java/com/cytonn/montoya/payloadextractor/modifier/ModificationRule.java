package com.cytonn.montoya.payloadextractor.modifier;

import com.cytonn.montoya.payloadextractor.intercept.InterceptDirection;
import com.cytonn.montoya.payloadextractor.util.Ids;
import com.cytonn.montoya.payloadextractor.util.JsonNode;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One deterministic find/replace rule for the "Automatic Editor" (e.g. {@code 555 -> 666} in every
 * URL path across every request to a given host). Rules run in list order ({@link RuleEngine}), each
 * independently enable/disable-able, scoped by host/path glob and by which part of the message they
 * touch.
 */
public final class ModificationRule {

    private final String id;
    private String name = "";
    private boolean enabled = true;
    private InterceptDirection direction = InterceptDirection.REQUEST;
    private RuleLocation location = RuleLocation.ANYWHERE;
    private String find = "";
    private String replaceWith = "";
    private boolean regex = false;
    private String hostScope = "";
    private String pathScope = "";

    public ModificationRule(String id) {
        this.id = id == null ? Ids.uuid() : id;
    }

    public String id() { return id; }
    public String name() { return name.isBlank() ? (find + " -> " + replaceWith) : name; }
    public void setName(String name) { this.name = name == null ? "" : name; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public InterceptDirection direction() { return direction; }
    public void setDirection(InterceptDirection direction) { this.direction = direction; }
    public RuleLocation location() { return location; }
    public void setLocation(RuleLocation location) { this.location = location; }
    public String find() { return find; }
    public void setFind(String find) { this.find = find == null ? "" : find; }
    public String replaceWith() { return replaceWith; }
    public void setReplaceWith(String replaceWith) { this.replaceWith = replaceWith == null ? "" : replaceWith; }
    public boolean isRegex() { return regex; }
    public void setRegex(boolean regex) { this.regex = regex; }
    public String hostScope() { return hostScope; }
    public void setHostScope(String hostScope) { this.hostScope = hostScope == null ? "" : hostScope; }
    public String pathScope() { return pathScope; }
    public void setPathScope(String pathScope) { this.pathScope = pathScope == null ? "" : pathScope; }

    public boolean inScope(String host, String path) {
        if (!hostScope.isBlank() && !globMatch(hostScope, host)) return false;
        if (!pathScope.isBlank() && !globMatch(pathScope, path)) return false;
        return true;
    }

    /** Applies find/replace to one chunk of text; returns the input unchanged if find is blank or doesn't match. */
    public String apply(String text) {
        if (text == null || find.isBlank()) {
            return text;
        }
        if (regex) {
            try {
                return Pattern.compile(find).matcher(text).replaceAll(java.util.regex.Matcher.quoteReplacement(replaceWith));
            } catch (PatternSyntaxException e) {
                return text;
            }
        }
        return text.replace(find, replaceWith);
    }

    /** Preview of what {@link #apply} would produce, for the rule editor's live preview. */
    public String preview(String sampleText) {
        return apply(sampleText);
    }

    private static boolean globMatch(String glob, String value) {
        if (value == null) return false;
        try {
            String regexPattern = "(?i)" + Pattern.quote(glob).replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q");
            return Pattern.matches(regexPattern, value);
        } catch (PatternSyntaxException e) {
            return glob.equalsIgnoreCase(value);
        }
    }

    // ---------------------------------------------------------------- persistence

    public String toJson() {
        return "{\"id\":" + q(id) + ",\"name\":" + q(name) + ",\"enabled\":" + enabled
                + ",\"direction\":" + q(direction.name()) + ",\"location\":" + q(location.name())
                + ",\"find\":" + q(find) + ",\"replaceWith\":" + q(replaceWith) + ",\"regex\":" + regex
                + ",\"hostScope\":" + q(hostScope) + ",\"pathScope\":" + q(pathScope) + "}";
    }

    public static ModificationRule fromJson(JsonNode n) {
        ModificationRule r = new ModificationRule(str(n.get("id")));
        r.name = safe(str(n.get("name")));
        r.enabled = n.get("enabled") == null || Boolean.parseBoolean(n.get("enabled").asString());
        try { r.direction = InterceptDirection.valueOf(str(n.get("direction"))); } catch (Exception ignored) { }
        try { r.location = RuleLocation.valueOf(str(n.get("location"))); } catch (Exception ignored) { }
        r.find = safe(str(n.get("find")));
        r.replaceWith = safe(str(n.get("replaceWith")));
        r.regex = n.get("regex") != null && Boolean.parseBoolean(n.get("regex").asString());
        r.hostScope = safe(str(n.get("hostScope")));
        r.pathScope = safe(str(n.get("pathScope")));
        return r;
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static String str(JsonNode n) { return n == null || n.isNull() ? null : n.asString(); }

    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                default: sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    @Override
    public String toString() {
        return (enabled ? "" : "[disabled] ") + name() + "  [" + location.displayName() + "]";
    }
}
