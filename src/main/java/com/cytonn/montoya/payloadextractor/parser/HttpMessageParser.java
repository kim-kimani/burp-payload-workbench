package com.cytonn.montoya.payloadextractor.parser;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.cytonn.montoya.payloadextractor.util.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Structural extraction: walks a real Montoya {@link HttpRequest}/{@link HttpResponse} and
 * produces {@link ParsedField} entries for every header, query/body/multipart/cookie parameter,
 * and JSON body leaf it can find. This stage does no "is this interesting" scoring or naming -
 * that is {@code PayloadDetector}'s job, layered on top of this raw structural pass.
 */
public final class HttpMessageParser {

    private static final Pattern XML_LEAF = Pattern.compile("<([A-Za-z0-9_:.-]+)(?:\\s[^>]*)?>([^<>]*)</\\1>");

    private HttpMessageParser() {
    }

    public static List<ParsedField> parseRequest(HttpRequest request) {
        List<ParsedField> fields = new ArrayList<>();
        if (request == null) {
            return fields;
        }

        String cookieHeaderValue = null;
        for (HttpHeader h : request.headers()) {
            if ("cookie".equalsIgnoreCase(h.name())) {
                cookieHeaderValue = h.value();
            } else {
                fields.add(ParsedField.builder()
                        .location(FieldLocation.HEADER)
                        .direction(MessageDirection.REQUEST)
                        .rawKey(h.name())
                        .headerName(h.name())
                        .originalValue(h.value())
                        .build());
            }
        }

        // Cookies are parsed from the raw "Cookie" header ourselves (rather than via Montoya's
        // HttpParameterType.COOKIE) so reading and RequestModifier's raw-header rebuild stay in
        // lockstep - the same splitting logic on both sides is what makes real reorder reliable.
        parseCookieHeader(fields, cookieHeaderValue);

        addParams(fields, request.parameters(HttpParameterType.URL), FieldLocation.URL_PARAM, MessageDirection.REQUEST, null);
        addParams(fields, request.parameters(HttpParameterType.BODY), FieldLocation.FORM_PARAM, MessageDirection.REQUEST, null);
        addParams(fields, request.parameters(HttpParameterType.MULTIPART_ATTRIBUTE), FieldLocation.MULTIPART_PARAM, MessageDirection.REQUEST, null);

        String body = safeBody(request.bodyToString());
        parseBody(fields, body, MessageDirection.REQUEST);

        return fields;
    }

    public static List<ParsedField> parseResponse(HttpResponse response) {
        List<ParsedField> fields = new ArrayList<>();
        if (response == null) {
            return fields;
        }

        for (HttpHeader h : response.headers()) {
            fields.add(ParsedField.builder()
                    .location(FieldLocation.HEADER)
                    .direction(MessageDirection.RESPONSE)
                    .rawKey(h.name())
                    .headerName(h.name())
                    .originalValue(h.value())
                    .build());
        }

        String body = safeBody(response.bodyToString());
        parseBody(fields, body, MessageDirection.RESPONSE);

        return fields;
    }

    private static void parseCookieHeader(List<ParsedField> out, String cookieHeaderValue) {
        if (cookieHeaderValue == null || cookieHeaderValue.isBlank()) {
            return;
        }
        for (String part : cookieHeaderValue.split(";\\s*")) {
            if (part.isBlank()) {
                continue;
            }
            int eq = part.indexOf('=');
            String name = eq >= 0 ? part.substring(0, eq) : part;
            String value = eq >= 0 ? part.substring(eq + 1) : "";
            out.add(ParsedField.builder()
                    .location(FieldLocation.COOKIE)
                    .direction(MessageDirection.REQUEST)
                    .rawKey(name)
                    .headerName("Cookie")
                    .originalValue(value)
                    .build());
        }
    }

    private static void addParams(List<ParsedField> out, List<ParsedHttpParameter> params, FieldLocation location,
                                   MessageDirection direction, String headerNameOverride) {
        for (ParsedHttpParameter p : params) {
            out.add(ParsedField.builder()
                    .location(location)
                    .direction(direction)
                    .rawKey(p.name())
                    .headerName(headerNameOverride)
                    .originalValue(p.value())
                    .build());
        }
    }

    private static void parseBody(List<ParsedField> out, String body, MessageDirection direction) {
        if (body == null || body.isBlank()) {
            return;
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                JsonNode root = JsonNode.parse(trimmed);
                root.flatten("", (path, leaf) -> {
                    if (!leaf.isNull()) {
                        out.add(ParsedField.builder()
                                .location(FieldLocation.JSON_BODY)
                                .direction(direction)
                                .rawKey(JsonNode.lastKeyOf(path) != null ? JsonNode.lastKeyOf(path) : path)
                                .path(path)
                                .originalValue(leaf.asString())
                                .build());
                    }
                });
                return;
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // not valid JSON - fall through to XML/raw handling below
            }
        }
        if (trimmed.startsWith("<")) {
            Matcher m = XML_LEAF.matcher(trimmed);
            int guard = 0;
            while (m.find() && guard++ < 2000) {
                String tag = m.group(1);
                String value = m.group(2);
                if (!value.isBlank()) {
                    out.add(ParsedField.builder()
                            .location(FieldLocation.XML_BODY)
                            .direction(direction)
                            .rawKey(tag)
                            .path(tag)
                            .originalValue(value)
                            .build());
                }
            }
        }
    }

    private static String safeBody(String s) {
        return s == null ? "" : s;
    }
}
