package com.cytonn.montoya.payloadextractor.intercept;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.cytonn.montoya.payloadextractor.util.Ids;
import com.cytonn.montoya.payloadextractor.util.JsonNode;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * One "Break On" / conditional-interception rule: every non-blank field set on it must match
 * (AND) for the condition to fire; {@link com.cytonn.montoya.payloadextractor.intercept.InterceptEngine}
 * OR's together every enabled condition in its list - matching Burp's own Proxy interception-rule
 * semantics (all fields within a rule AND, all rules in the list OR). An empty, all-blank condition
 * list means "intercept everything" whenever the corresponding master/direction checkbox is on.
 */
public final class InterceptCondition {

    private final String id;
    private String label;
    private boolean enabled = true;

    private String host = "";           // glob, e.g. *.example.com
    private String path = "";           // glob or substring
    private String method = "";         // exact, e.g. POST
    private String statusCode = "";     // exact or comparator: "500", ">399", "<300"
    private String headerName = "";
    private String headerValueContains = "";
    private String cookieName = "";
    private String parameterName = "";
    private String bodyContains = "";
    private boolean regex = false;
    private Long responseSizeGreaterThanBytes = null;
    private boolean newEndpointOnly = false;
    private boolean newParameterOnly = false;

    public InterceptCondition(String id) {
        this.id = id == null ? Ids.uuid() : id;
    }

    public static InterceptCondition preset(String label, String bodyContains, String statusCode) {
        InterceptCondition c = new InterceptCondition(null);
        c.label = label;
        c.bodyContains = bodyContains == null ? "" : bodyContains;
        c.statusCode = statusCode == null ? "" : statusCode;
        return c;
    }

    public String id() { return id; }
    public String label() { return label == null || label.isBlank() ? summarize() : label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String host() { return host; }
    public void setHost(String host) { this.host = safe(host); }
    public String path() { return path; }
    public void setPath(String path) { this.path = safe(path); }
    public String method() { return method; }
    public void setMethod(String method) { this.method = safe(method); }
    public String statusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = safe(statusCode); }
    public String headerName() { return headerName; }
    public void setHeaderName(String headerName) { this.headerName = safe(headerName); }
    public String headerValueContains() { return headerValueContains; }
    public void setHeaderValueContains(String v) { this.headerValueContains = safe(v); }
    public String cookieName() { return cookieName; }
    public void setCookieName(String cookieName) { this.cookieName = safe(cookieName); }
    public String parameterName() { return parameterName; }
    public void setParameterName(String parameterName) { this.parameterName = safe(parameterName); }
    public String bodyContains() { return bodyContains; }
    public void setBodyContains(String bodyContains) { this.bodyContains = safe(bodyContains); }
    public boolean isRegex() { return regex; }
    public void setRegex(boolean regex) { this.regex = regex; }
    public Long responseSizeGreaterThanBytes() { return responseSizeGreaterThanBytes; }
    public void setResponseSizeGreaterThanBytes(Long v) { this.responseSizeGreaterThanBytes = v; }
    public boolean isNewEndpointOnly() { return newEndpointOnly; }
    public void setNewEndpointOnly(boolean v) { this.newEndpointOnly = v; }
    public boolean isNewParameterOnly() { return newParameterOnly; }
    public void setNewParameterOnly(boolean v) { this.newParameterOnly = v; }

    private static String safe(String s) { return s == null ? "" : s; }

    // ---------------------------------------------------------------- matching

    public boolean matchesRequest(HttpRequest request, String host, boolean isNewEndpoint, boolean isNewParameter) {
        if (!enabled) return false;
        if (newEndpointOnly && !isNewEndpoint) return false;
        if (newParameterOnly && !isNewParameter) return false;
        if (!blank(this.host) && !globMatch(this.host, host)) return false;
        if (!blank(path) && !containsOrGlob(path, request.path())) return false;
        if (!blank(method) && !method.equalsIgnoreCase(request.method())) return false;
        if (!blank(headerName)) {
            HttpHeader h = request.header(headerName);
            if (h == null) return false;
            if (!blank(headerValueContains) && !textMatches(headerValueContains, h.value())) return false;
        }
        if (!blank(cookieName)) {
            HttpHeader cookie = request.header("Cookie");
            if (cookie == null || !cookie.value().contains(cookieName + "=")) return false;
        }
        if (!blank(parameterName)) {
            boolean has = request.hasParameter(parameterName, burp.api.montoya.http.message.params.HttpParameterType.URL)
                    || request.hasParameter(parameterName, burp.api.montoya.http.message.params.HttpParameterType.BODY);
            if (!has && !request.bodyToString().contains("\"" + parameterName + "\"")) return false;
        }
        if (!blank(bodyContains) && !textMatches(bodyContains, request.bodyToString())) return false;
        return anyFieldSet() || newEndpointOnly || newParameterOnly;
    }

    public boolean matchesResponse(HttpResponse response, String host, long responseSizeBytes) {
        if (!enabled) return false;
        if (!blank(this.host) && !globMatch(this.host, host)) return false;
        if (!blank(statusCode) && !statusMatches(statusCode, response.statusCode())) return false;
        if (!blank(headerName)) {
            HttpHeader h = response.header(headerName);
            if (h == null) return false;
            if (!blank(headerValueContains) && !textMatches(headerValueContains, h.value())) return false;
        }
        if (!blank(bodyContains) && !textMatches(bodyContains, response.bodyToString())) return false;
        if (responseSizeGreaterThanBytes != null && responseSizeBytes <= responseSizeGreaterThanBytes) return false;
        return anyFieldSet();
    }

    /** A condition with every field blank never matches on its own (it would mean "match nothing" here, not "match everything" - that's the empty-list case in the engine instead). */
    private boolean anyFieldSet() {
        return !blank(host) || !blank(path) || !blank(method) || !blank(statusCode) || !blank(headerName)
                || !blank(cookieName) || !blank(parameterName) || !blank(bodyContains) || responseSizeGreaterThanBytes != null;
    }

    private boolean textMatches(String needle, String haystack) {
        if (haystack == null) return false;
        if (regex) {
            try {
                return Pattern.compile(needle, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(haystack).find();
            } catch (PatternSyntaxException e) {
                return haystack.toLowerCase().contains(needle.toLowerCase());
            }
        }
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private boolean containsOrGlob(String pattern, String value) {
        if (pattern.contains("*") || pattern.contains("?")) {
            return globMatch(pattern, value);
        }
        return value != null && value.toLowerCase().contains(pattern.toLowerCase());
    }

    private static boolean globMatch(String glob, String value) {
        if (value == null) return false;
        try {
            String regex = "(?i)" + Pattern.quote(glob).replace("*", "\\E.*\\Q").replace("?", "\\E.\\Q");
            return Pattern.matches(regex, value);
        } catch (PatternSyntaxException e) {
            return glob.equalsIgnoreCase(value);
        }
    }

    private static boolean statusMatches(String spec, short actual) {
        spec = spec.trim();
        try {
            if (spec.startsWith(">=")) return actual >= Short.parseShort(spec.substring(2).trim());
            if (spec.startsWith("<=")) return actual <= Short.parseShort(spec.substring(2).trim());
            if (spec.startsWith(">")) return actual > Short.parseShort(spec.substring(1).trim());
            if (spec.startsWith("<")) return actual < Short.parseShort(spec.substring(1).trim());
            return actual == Short.parseShort(spec);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private String summarize() {
        if (!blank(statusCode)) return "Status " + statusCode;
        if (!blank(bodyContains)) return "Contains \"" + bodyContains + "\"";
        if (!blank(path)) return "Path " + path;
        if (!blank(host)) return "Host " + host;
        if (responseSizeGreaterThanBytes != null) return "Size > " + responseSizeGreaterThanBytes + "B";
        if (newEndpointOnly) return "New endpoint";
        if (newParameterOnly) return "New parameter";
        return "Condition";
    }

    // ---------------------------------------------------------------- persistence

    public String toJson() {
        return "{"
                + "\"id\":" + q(id) + ",\"label\":" + q(label) + ",\"enabled\":" + enabled
                + ",\"host\":" + q(host) + ",\"path\":" + q(path) + ",\"method\":" + q(method)
                + ",\"statusCode\":" + q(statusCode) + ",\"headerName\":" + q(headerName)
                + ",\"headerValueContains\":" + q(headerValueContains) + ",\"cookieName\":" + q(cookieName)
                + ",\"parameterName\":" + q(parameterName) + ",\"bodyContains\":" + q(bodyContains)
                + ",\"regex\":" + regex
                + ",\"responseSizeGreaterThanBytes\":" + (responseSizeGreaterThanBytes == null ? "null" : responseSizeGreaterThanBytes)
                + ",\"newEndpointOnly\":" + newEndpointOnly + ",\"newParameterOnly\":" + newParameterOnly
                + "}";
    }

    public static InterceptCondition fromJson(JsonNode n) {
        InterceptCondition c = new InterceptCondition(str(n.get("id")));
        c.label = str(n.get("label"));
        c.enabled = n.get("enabled") == null || Boolean.parseBoolean(n.get("enabled").asString());
        c.host = safe(str(n.get("host")));
        c.path = safe(str(n.get("path")));
        c.method = safe(str(n.get("method")));
        c.statusCode = safe(str(n.get("statusCode")));
        c.headerName = safe(str(n.get("headerName")));
        c.headerValueContains = safe(str(n.get("headerValueContains")));
        c.cookieName = safe(str(n.get("cookieName")));
        c.parameterName = safe(str(n.get("parameterName")));
        c.bodyContains = safe(str(n.get("bodyContains")));
        c.regex = n.get("regex") != null && Boolean.parseBoolean(n.get("regex").asString());
        JsonNode sizeNode = n.get("responseSizeGreaterThanBytes");
        if (sizeNode != null && !sizeNode.isNull()) {
            try { c.responseSizeGreaterThanBytes = Long.parseLong(sizeNode.asString()); } catch (NumberFormatException ignored) { }
        }
        c.newEndpointOnly = n.get("newEndpointOnly") != null && Boolean.parseBoolean(n.get("newEndpointOnly").asString());
        c.newParameterOnly = n.get("newParameterOnly") != null && Boolean.parseBoolean(n.get("newParameterOnly").asString());
        return c;
    }

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
        return (enabled ? "" : "[disabled] ") + label();
    }
}
