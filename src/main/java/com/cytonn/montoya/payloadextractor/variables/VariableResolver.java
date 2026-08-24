package com.cytonn.montoya.payloadextractor.variables;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code {{NAME}}} placeholders in outgoing text - path/query, headers (including
 * Cookie), and body - against a {@link VariableStore}, at the moment a request is actually about
 * to be sent (Workbench's "Send Modified Request", Replay, and Intercept's "Forward & Edit"). The
 * placeholder text stays visible everywhere it's typed/displayed; only the wire-level request that
 * goes out gets resolved values.
 *
 * <p>Three reserved names are always dynamic, generated fresh on every resolve rather than looked
 * up in the store: {@code {{UUID}}}, {@code {{TIMESTAMP}}} (epoch millis), {@code {{RANDOM}}} (a
 * random 6-digit-ish integer). Anything else is looked up by (case-insensitive, normalized) name;
 * an unknown name is left untouched so a typo doesn't silently blank out part of the request.
 */
public final class VariableResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_]+)}}");

    private VariableResolver() {
    }

    public static String resolve(String text, VariableStore store) {
        if (text == null || text.isEmpty() || !text.contains("{{")) {
            return text;
        }
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String name = m.group(1).toUpperCase(Locale.ROOT);
            String replacement = dynamicValue(name);
            if (replacement == null) {
                String stored = store == null ? null : store.get(name);
                replacement = stored != null ? stored : m.group(0);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** {@code null} means "not a reserved dynamic name" - fall through to a store lookup. */
    private static String dynamicValue(String reservedName) {
        switch (reservedName) {
            case "UUID":
                return UUID.randomUUID().toString();
            case "TIMESTAMP":
                return String.valueOf(System.currentTimeMillis());
            case "RANDOM":
                return String.valueOf(java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 1_000_000));
            default:
                return null;
        }
    }

    /** Resolves every placeholder in a request's path/query, headers (Cookie included), and body; returns the same object if nothing changed. */
    public static HttpRequest resolveInRequest(HttpRequest request, VariableStore store) {
        if (request == null) {
            return request;
        }
        HttpRequest updated = request;

        String path = updated.path();
        String newPath = resolve(path, store);
        if (!newPath.equals(path)) {
            updated = updated.withPath(newPath);
        }

        for (HttpHeader h : request.headers()) {
            String newValue = resolve(h.value(), store);
            if (!newValue.equals(h.value())) {
                updated = updated.withUpdatedHeader(h.name(), newValue);
            }
        }

        String body = updated.bodyToString();
        String newBody = resolve(body, store);
        if (!newBody.equals(body)) {
            updated = updated.withBody(newBody);
        }

        return updated;
    }
}
