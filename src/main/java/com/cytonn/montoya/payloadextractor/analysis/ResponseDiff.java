package com.cytonn.montoya.payloadextractor.analysis;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.cytonn.montoya.payloadextractor.util.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Response Analysis (item 7): status/size/header/JSON-structural diffing between two responses -
 * typically a baseline (e.g. a replay run's first step, or an identity's response) and a candidate
 * (a later step, or a different identity's response to the same request). Every comparison is a
 * plain, deterministic diff; {@link DiffResult#interesting} is a heuristic flag for "worth a second
 * look", not a vulnerability claim - that judgment call stays with the analyst.
 */
public final class ResponseDiff {

    /** Relative size change (as a fraction of the baseline) above which a size difference is flagged as significant. */
    private static final double SIZE_CHANGE_THRESHOLD = 0.15;
    /** Absolute size change floor so tiny responses (a few bytes either way) don't get flagged on rounding noise. */
    private static final long SIZE_CHANGE_FLOOR_BYTES = 32;

    private ResponseDiff() {
    }

    public static DiffResult compare(HttpResponse baseline, HttpResponse candidate) {
        DiffResult result = new DiffResult();

        result.oldStatus = baseline != null ? (int) baseline.statusCode() : null;
        result.newStatus = candidate != null ? (int) candidate.statusCode() : null;
        result.statusChanged = !java.util.Objects.equals(result.oldStatus, result.newStatus);

        result.oldSizeBytes = baseline != null ? (long) baseline.toByteArray().length() : null;
        result.newSizeBytes = candidate != null ? (long) candidate.toByteArray().length() : null;
        if (result.oldSizeBytes != null && result.newSizeBytes != null) {
            result.sizeDeltaBytes = result.newSizeBytes - result.oldSizeBytes;
            long absDelta = Math.abs(result.sizeDeltaBytes);
            double relative = result.oldSizeBytes == 0 ? (absDelta > 0 ? 1.0 : 0.0) : (double) absDelta / result.oldSizeBytes;
            result.sizeChangedSignificantly = absDelta >= SIZE_CHANGE_FLOOR_BYTES && relative >= SIZE_CHANGE_THRESHOLD;
        }

        if (baseline != null && candidate != null) {
            diffHeaders(baseline.headers(), candidate.headers(), result);
            diffBody(bodySafe(baseline), bodySafe(candidate), result);
        }

        result.interesting = result.statusChanged || result.sizeChangedSignificantly
                || !result.headersAdded.isEmpty() || !result.headersRemoved.isEmpty()
                || !result.jsonFieldsAdded.isEmpty() || !result.jsonFieldsRemoved.isEmpty();

        return result;
    }

    private static void diffHeaders(java.util.List<HttpHeader> a, java.util.List<HttpHeader> b, DiffResult result) {
        Map<String, String> before = new LinkedHashMap<>();
        for (HttpHeader h : a) before.put(h.name().toLowerCase(), h.value());
        Map<String, String> after = new LinkedHashMap<>();
        for (HttpHeader h : b) after.put(h.name().toLowerCase(), h.value());

        for (String name : after.keySet()) {
            if (!before.containsKey(name)) {
                result.headersAdded.add(name);
            } else if (!before.get(name).equals(after.get(name))) {
                result.headersChanged.add(new DiffResult.FieldChange(name, before.get(name), after.get(name)));
            }
        }
        for (String name : before.keySet()) {
            if (!after.containsKey(name)) {
                result.headersRemoved.add(name);
            }
        }
    }

    private static void diffBody(String before, String after, DiffResult result) {
        if (before.equals(after)) {
            return;
        }
        if (looksLikeJson(before) && looksLikeJson(after)) {
            try {
                diffJson(JsonNode.parse(before), JsonNode.parse(after), result);
                return;
            } catch (Exception ignored) {
                // fall through to the plain-text comparison below
            }
        }
        result.bodyDiffersNonJson = true;
    }

    private static void diffJson(JsonNode before, JsonNode after, DiffResult result) {
        Map<String, String> beforeFlat = new LinkedHashMap<>();
        before.flatten("", (path, node) -> beforeFlat.put(path, node.isNull() ? "null" : node.asString()));
        Map<String, String> afterFlat = new LinkedHashMap<>();
        after.flatten("", (path, node) -> afterFlat.put(path, node.isNull() ? "null" : node.asString()));

        for (Map.Entry<String, String> e : afterFlat.entrySet()) {
            if (!beforeFlat.containsKey(e.getKey())) {
                result.jsonFieldsAdded.add(new DiffResult.FieldChange(e.getKey(), null, e.getValue()));
            } else if (!java.util.Objects.equals(beforeFlat.get(e.getKey()), e.getValue())) {
                result.jsonFieldsChanged.add(new DiffResult.FieldChange(e.getKey(), beforeFlat.get(e.getKey()), e.getValue()));
            }
        }
        for (String key : beforeFlat.keySet()) {
            if (!afterFlat.containsKey(key)) {
                result.jsonFieldsRemoved.add(new DiffResult.FieldChange(key, beforeFlat.get(key), null));
            }
        }
    }

    private static String bodySafe(HttpResponse response) {
        try {
            return response.bodyToString();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean looksLikeJson(String body) {
        String t = body == null ? "" : body.trim();
        return t.startsWith("{") || t.startsWith("[");
    }
}
