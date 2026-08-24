package com.cytonn.montoya.payloadextractor.variables;

import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.db.PayloadCollection;
import com.cytonn.montoya.payloadextractor.db.PayloadValue;
import com.cytonn.montoya.payloadextractor.history.HistoryEntry;
import com.cytonn.montoya.payloadextractor.intercept.InterceptedMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * "Track Value": a read-only scan of everything the extension already knows about (the History
 * log, the Intercept request history, and remembered Payload Collections) for occurrences of one
 * value - item 5's "show everywhere a value appears across traffic". Introduces no storage of its
 * own; it only reads what {@link ExtensionState}'s existing subsystems already hold.
 */
public final class ValueTracker {

    public static final class Occurrence {
        private final String source;
        private final String host;
        private final String detail;
        private final long whenEpochMillis;

        Occurrence(String source, String host, String detail, long whenEpochMillis) {
            this.source = source;
            this.host = host == null ? "" : host;
            this.detail = detail == null ? "" : detail;
            this.whenEpochMillis = whenEpochMillis;
        }

        public String source() { return source; }
        public String host() { return host; }
        public String detail() { return detail; }
        public long whenEpochMillis() { return whenEpochMillis; }
    }

    private ValueTracker() {
    }

    public static List<Occurrence> find(String value, ExtensionState state) {
        List<Occurrence> out = new ArrayList<>();
        if (value == null || value.isBlank() || state == null) {
            return out;
        }

        for (HistoryEntry e : state.historyManager().all()) {
            if (matches(e.oldValue(), value) || matches(e.newValue(), value)) {
                out.add(new Occurrence("History", e.host(),
                        e.action() + " - " + e.fieldName() + (e.location() != null ? " (" + e.location().displayName() + ")" : ""),
                        e.timestampEpochMillis()));
            }
        }

        for (InterceptedMessage m : state.interceptEngine().history()) {
            boolean inRequest = m.currentRequest() != null
                    && (matches(m.currentRequest().path(), value) || matches(bodySafe(() -> m.currentRequest().bodyToString()), value)
                    || headersContain(m.currentRequest().headers(), value));
            boolean inResponse = m.currentResponse() != null
                    && (matches(bodySafe(() -> m.currentResponse().bodyToString()), value) || headersContain(m.currentResponse().headers(), value));
            if (inRequest) {
                out.add(new Occurrence("Intercept (request)", m.host(), m.method() + " " + m.path(), m.timestampEpochMillis()));
            }
            if (inResponse) {
                out.add(new Occurrence("Intercept (response)", m.host(), m.method() + " " + m.path(), m.timestampEpochMillis()));
            }
        }

        for (PayloadCollection c : state.database().allCollections()) {
            for (PayloadValue v : c.values()) {
                if (matches(v.value(), value)) {
                    out.add(new Occurrence("Payload Collection", v.originHost(), c.normalizedName() + " (" + c.category() + ")", v.capturedAtEpochMillis()));
                }
            }
        }

        return out;
    }

    private static boolean matches(String haystack, String needle) {
        return haystack != null && haystack.contains(needle);
    }

    private static boolean headersContain(List<burp.api.montoya.http.message.HttpHeader> headers, String value) {
        if (headers == null) return false;
        for (burp.api.montoya.http.message.HttpHeader h : headers) {
            if (matches(h.value(), value)) return true;
        }
        return false;
    }

    private static String bodySafe(java.util.function.Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return null;
        }
    }
}
