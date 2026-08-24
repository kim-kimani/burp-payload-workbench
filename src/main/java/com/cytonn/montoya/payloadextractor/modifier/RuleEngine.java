package com.cytonn.montoya.payloadextractor.modifier;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Automatic Modification Engine": an ordered, independently enable/disable-able list of
 * {@link ModificationRule}s, applied deterministically to every request/response that passes
 * through {@code InterceptEngine} when its own "Automatic Editor" toggle is on. Runs entirely on
 * top of the real Montoya {@code HttpRequest}/{@code HttpResponse} immutable-builder APIs - no
 * separate HTTP engine, no raw-socket text hacking.
 */
public final class RuleEngine {

    private volatile boolean enabled = false;
    private final List<ModificationRule> rules = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<ModificationRule> rules() { return rules; }

    public ModificationRule addRule() {
        ModificationRule r = new ModificationRule(null);
        rules.add(r);
        return r;
    }

    public void removeRule(String id) {
        rules.removeIf(r -> r.id().equals(id));
    }

    public void moveUp(String id) {
        int i = indexOf(id);
        if (i > 0) {
            java.util.Collections.swap(rules, i, i - 1);
        }
    }

    public void moveDown(String id) {
        int i = indexOf(id);
        if (i >= 0 && i < rules.size() - 1) {
            java.util.Collections.swap(rules, i, i + 1);
        }
    }

    private int indexOf(String id) {
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    /** Applies every enabled, in-scope, request-side rule in order. Returns the same object if nothing changed or the engine is off. */
    public HttpRequest applyToRequest(HttpRequest request, String host) {
        if (!enabled || request == null) {
            return request;
        }
        HttpRequest result = request;
        for (ModificationRule rule : rules) {
            if (!rule.isEnabled() || !rule.direction().appliesToRequest() || !rule.location().isRequestSide()) {
                continue;
            }
            if (!rule.inScope(host, result.pathWithoutQuery())) {
                continue;
            }
            result = applyOneToRequest(result, rule);
        }
        return result;
    }

    /** Applies every enabled, in-scope, response-side rule in order. */
    public HttpResponse applyToResponse(HttpResponse response, String host, String path) {
        if (!enabled || response == null) {
            return response;
        }
        HttpResponse result = response;
        for (ModificationRule rule : rules) {
            if (!rule.isEnabled() || !rule.direction().appliesToResponse() || !rule.location().isResponseSide()) {
                continue;
            }
            if (!rule.inScope(host, path)) {
                continue;
            }
            result = applyOneToResponse(result, rule);
        }
        return result;
    }

    private HttpRequest applyOneToRequest(HttpRequest request, ModificationRule rule) {
        switch (rule.location()) {
            case PATH: {
                String p = rule.apply(request.pathWithoutQuery());
                String query = request.query();
                return request.withPath(query.isBlank() ? p : p + "?" + query);
            }
            case QUERY: {
                String q = rule.apply(request.query());
                String path = request.pathWithoutQuery();
                return request.withPath(q.isBlank() ? path : path + "?" + q);
            }
            case HEADERS: {
                HttpRequest updated = request;
                for (HttpHeader h : request.headers()) {
                    if ("cookie".equalsIgnoreCase(h.name())) {
                        continue;
                    }
                    String newValue = rule.apply(h.value());
                    if (!newValue.equals(h.value())) {
                        updated = updated.withUpdatedHeader(h.name(), newValue);
                    }
                }
                return updated;
            }
            case COOKIES: {
                HttpHeader cookieHeader = request.header("Cookie");
                if (cookieHeader == null) {
                    return request;
                }
                String newValue = rule.apply(cookieHeader.value());
                return newValue.equals(cookieHeader.value()) ? request : request.withUpdatedHeader("Cookie", newValue);
            }
            case JSON_BODY: {
                String body = request.bodyToString();
                if (!looksLikeJson(body)) return request;
                String newBody = rule.apply(body);
                return newBody.equals(body) ? request : request.withBody(newBody);
            }
            case FORM_BODY: {
                String body = request.bodyToString();
                if (!looksLikeForm(request, body)) return request;
                String newBody = rule.apply(body);
                return newBody.equals(body) ? request : request.withBody(newBody);
            }
            case RAW_BODY: {
                String body = request.bodyToString();
                String newBody = rule.apply(body);
                return newBody.equals(body) ? request : request.withBody(newBody);
            }
            case ANYWHERE: {
                HttpRequest updated = applyOneToRequest(request, withLocation(rule, RuleLocation.PATH));
                updated = withHeadersApplied(updated, rule);
                String body = updated.bodyToString();
                String newBody = rule.apply(body);
                if (!newBody.equals(body)) {
                    updated = updated.withBody(newBody);
                }
                return updated;
            }
            default:
                return request;
        }
    }

    private HttpRequest withHeadersApplied(HttpRequest request, ModificationRule rule) {
        HttpRequest updated = request;
        for (HttpHeader h : request.headers()) {
            String newValue = rule.apply(h.value());
            if (!newValue.equals(h.value())) {
                updated = updated.withUpdatedHeader(h.name(), newValue);
            }
        }
        return updated;
    }

    private HttpResponse applyOneToResponse(HttpResponse response, ModificationRule rule) {
        switch (rule.location()) {
            case RESPONSE_HEADERS: {
                HttpResponse updated = response;
                for (HttpHeader h : response.headers()) {
                    String newValue = rule.apply(h.value());
                    if (!newValue.equals(h.value())) {
                        updated = updated.withUpdatedHeader(h.name(), newValue);
                    }
                }
                return updated;
            }
            case RESPONSE_BODY:
            case ANYWHERE: {
                String body = response.bodyToString();
                String newBody = rule.apply(body);
                HttpResponse updated = newBody.equals(body) ? response : response.withBody(newBody);
                if (rule.location() == RuleLocation.ANYWHERE) {
                    HttpResponse withHeaders = updated;
                    for (HttpHeader h : updated.headers()) {
                        String newValue = rule.apply(h.value());
                        if (!newValue.equals(h.value())) {
                            withHeaders = withHeaders.withUpdatedHeader(h.name(), newValue);
                        }
                    }
                    return withHeaders;
                }
                return updated;
            }
            default:
                return response;
        }
    }

    private static boolean looksLikeJson(String body) {
        String t = body == null ? "" : body.trim();
        return t.startsWith("{") || t.startsWith("[");
    }

    private static boolean looksLikeForm(HttpRequest request, String body) {
        HttpHeader ct = request.header("Content-Type");
        if (ct != null && ct.value().toLowerCase().contains("x-www-form-urlencoded")) {
            return true;
        }
        return body != null && !looksLikeJson(body) && body.contains("=");
    }

    /** Shallow copy of a rule with a different location, used by the ANYWHERE case to reuse the PATH branch's logic. */
    private static ModificationRule withLocation(ModificationRule rule, RuleLocation loc) {
        ModificationRule copy = new ModificationRule(rule.id());
        copy.setName(rule.name());
        copy.setEnabled(rule.isEnabled());
        copy.setDirection(rule.direction());
        copy.setLocation(loc);
        copy.setFind(rule.find());
        copy.setReplaceWith(rule.replaceWith());
        copy.setRegex(rule.isRegex());
        copy.setHostScope(rule.hostScope());
        copy.setPathScope(rule.pathScope());
        return copy;
    }

    // ---------------------------------------------------------------- persistence

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"enabled\":").append(enabled).append(",\"rules\":[");
        for (int i = 0; i < rules.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(rules.get(i).toJson());
        }
        sb.append("]}");
        return sb.toString();
    }

    public static RuleEngine fromJson(String json) {
        RuleEngine engine = new RuleEngine();
        if (json == null || json.isBlank()) {
            return engine;
        }
        try {
            com.cytonn.montoya.payloadextractor.util.JsonNode root = com.cytonn.montoya.payloadextractor.util.JsonNode.parse(json);
            com.cytonn.montoya.payloadextractor.util.JsonNode enabledNode = root.get("enabled");
            if (enabledNode != null) {
                engine.enabled = Boolean.parseBoolean(enabledNode.asString());
            }
            com.cytonn.montoya.payloadextractor.util.JsonNode rulesNode = root.get("rules");
            if (rulesNode != null && rulesNode.isArray()) {
                for (int i = 0; i < rulesNode.size(); i++) {
                    engine.rules.add(ModificationRule.fromJson(rulesNode.get(i)));
                }
            }
        } catch (Exception ignored) {
        }
        return engine;
    }
}
