package com.cytonn.montoya.payloadextractor.modifier;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.HttpParameterType;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.cytonn.montoya.payloadextractor.parser.FieldLocation;
import com.cytonn.montoya.payloadextractor.parser.MessageDirection;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;
import com.cytonn.montoya.payloadextractor.util.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns the Workbench's field list into a real, wire-level {@link HttpRequest} - the piece that
 * makes Add / Duplicate / drag-reorder / the "X" remove button actually change the composed
 * request body and headers, not just the on-screen boxes.
 *
 * <p>{@link #buildComposedRequest} always starts from the pristine {@code originalRequest} (never
 * from a previously-modified one), so repeated edits can never drift or compound - this is the
 * "guaranteed original-request preservation" guarantee the rest of the extension relies on. It
 * reconciles two field lists: {@code baselineFields} (what {@code PayloadDetector} found in the
 * pristine original, captured once when the Workbench opened) against {@code workingFields} (the
 * Workbench's current list, after any Add/Duplicate/drag-reorder/remove/value-edit the analyst has
 * made) - anything only in baseline is removed, anything only in working is added, anything in
 * both is value-substituted, and for the two container kinds we can genuinely reorder
 * (JSON body objects and the Cookie header) the working list's order becomes the real order.
 *
 * <p>Real wire-order reorder is only possible for {@link FieldLocation#JSON_BODY} and
 * {@link FieldLocation#COOKIE} - see {@link FieldLocation#supportsRealReorder()}. Montoya exposes
 * no positional-insert API for headers or URL/form/multipart parameters, so those support real
 * add/remove but keep whatever order Montoya itself assigns newly-added entries (appended) and
 * preserves for existing ones.
 */
public final class RequestModifier {

    private static final String XML_TAG_TEMPLATE = "<(%s)(?:\\s[^>]*)?>([^<>]*)</\\1>";

    private RequestModifier() {
    }

    public static HttpRequest buildComposedRequest(HttpRequest originalRequest, List<ParsedField> baselineFields, List<ParsedField> workingFields) {
        HttpRequest request = originalRequest;

        List<ParsedField> baseline = onlyRequest(baselineFields);
        List<ParsedField> working = onlyRequest(workingFields);

        request = applyJsonBody(request, baseline, working);
        request = applyCookies(request, baseline, working);
        request = applyHeaders(request, baseline, working);
        request = applyParams(request, baseline, working, FieldLocation.URL_PARAM, HttpParameterType.URL);
        request = applyParams(request, baseline, working, FieldLocation.FORM_PARAM, HttpParameterType.BODY);
        request = applyParams(request, baseline, working, FieldLocation.MULTIPART_PARAM, HttpParameterType.MULTIPART_ATTRIBUTE);
        request = applyXmlBody(request, working);

        return request;
    }

    /**
     * Lightweight single-field value swap, used by the replay engine's hot loop: {@code baseRequest}
     * is assumed already fully composed (structure settled), only {@code field}'s value changes.
     */
    public static HttpRequest substituteSingleValue(HttpRequest baseRequest, ParsedField field, String newValue) {
        switch (field.location()) {
            case JSON_BODY: {
                JsonNode root = parseBodyOrNull(baseRequest.bodyToString());
                if (root == null) {
                    return baseRequest;
                }
                JsonNode updated = root.withReplacedPath(field.path(), newValue);
                return baseRequest.withBody(updated.toCompactJson());
            }
            case COOKIE: {
                HttpHeader cookieHeader = baseRequest.header("Cookie");
                if (cookieHeader == null) {
                    return baseRequest;
                }
                String rebuilt = replaceCookiePair(cookieHeader.value(), field.rawKey(), newValue);
                return baseRequest.withUpdatedHeader("Cookie", rebuilt);
            }
            case HEADER:
                return baseRequest.hasHeader(field.headerName() != null ? field.headerName() : field.rawKey())
                        ? baseRequest.withUpdatedHeader(field.headerName() != null ? field.headerName() : field.rawKey(), newValue)
                        : baseRequest.withAddedHeader(field.headerName() != null ? field.headerName() : field.rawKey(), newValue);
            case URL_PARAM:
                return baseRequest.withUpdatedParameters(HttpParameter.parameter(field.rawKey(), newValue, HttpParameterType.URL));
            case FORM_PARAM:
                return baseRequest.withUpdatedParameters(HttpParameter.parameter(field.rawKey(), newValue, HttpParameterType.BODY));
            case MULTIPART_PARAM:
                return baseRequest.withUpdatedParameters(HttpParameter.parameter(field.rawKey(), newValue, HttpParameterType.MULTIPART_ATTRIBUTE));
            case XML_BODY: {
                String body = baseRequest.bodyToString();
                String replaced = replaceXmlLeaf(body, field.rawKey(), newValue);
                return baseRequest.withBody(replaced);
            }
            case RAW_BODY: {
                String body = baseRequest.bodyToString();
                String replaced = body.replace(field.originalValue(), newValue);
                return baseRequest.withBody(replaced);
            }
            default:
                return baseRequest;
        }
    }

    // ---------------------------------------------------------------- JSON body

    private static HttpRequest applyJsonBody(HttpRequest request, List<ParsedField> baseline, List<ParsedField> working) {
        List<ParsedField> baselineJson = filter(baseline, FieldLocation.JSON_BODY);
        List<ParsedField> workingJson = filter(working, FieldLocation.JSON_BODY);
        if (baselineJson.isEmpty() && workingJson.isEmpty()) {
            return request;
        }

        JsonNode root = parseBodyOrNull(request.bodyToString());
        if (root == null) {
            root = JsonNode.parse("{}");
        }

        // Group by parent object path, preserving group discovery order.
        Set<String> parentPaths = new LinkedHashSet<>();
        Map<String, ParsedField> baselineByPath = new LinkedHashMap<>();
        for (ParsedField f : baselineJson) {
            parentPaths.add(JsonNode.parentPathOf(f.path()));
            baselineByPath.put(f.path(), f);
        }
        Map<String, List<ParsedField>> workingByParent = new LinkedHashMap<>();
        for (ParsedField f : workingJson) {
            String parent = JsonNode.parentPathOf(f.path());
            parentPaths.add(parent);
            workingByParent.computeIfAbsent(parent, p -> new ArrayList<>()).add(f);
        }

        for (String parent : parentPaths) {
            List<ParsedField> baselineAtParent = baselineJson.stream()
                    .filter(f -> JsonNode.parentPathOf(f.path()).equals(parent))
                    .collect(Collectors.toList());
            List<ParsedField> workingAtParent = workingByParent.getOrDefault(parent, List.of());

            Set<String> baselineKeys = baselineAtParent.stream().map(f -> JsonNode.lastKeyOf(f.path())).collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> workingKeys = workingAtParent.stream().map(f -> JsonNode.lastKeyOf(f.path())).collect(Collectors.toCollection(LinkedHashSet::new));

            // 1) remove keys that dropped out of the working list
            for (ParsedField f : baselineAtParent) {
                String key = JsonNode.lastKeyOf(f.path());
                if (key != null && !workingKeys.contains(key) && objectHasKey(root, parent, key)) {
                    root = root.withRemovedPath(joinPath(parent, key));
                }
            }

            // 2) add keys that are new (manually added/duplicated) - appended, order fixed up next
            for (ParsedField f : workingAtParent) {
                String key = JsonNode.lastKeyOf(f.path());
                if (key != null && !baselineKeys.contains(key)) {
                    root = root.withAddedKey(parent, key, f.currentValue(), -1);
                }
            }

            // 3) substitute values for keys present in both
            for (ParsedField f : workingAtParent) {
                String key = JsonNode.lastKeyOf(f.path());
                if (key != null && baselineKeys.contains(key) && objectHasKey(root, parent, key)) {
                    root = root.withReplacedPath(joinPath(parent, key), f.currentValue());
                }
            }

            // 4) enforce the working list's order (this is the real drag-to-reorder effect)
            int index = 0;
            for (ParsedField f : workingAtParent) {
                String key = JsonNode.lastKeyOf(f.path());
                if (key != null && objectHasKey(root, parent, key)) {
                    root = root.withReorderedKey(joinPath(parent, key), index);
                    index++;
                }
            }
        }

        return request.withBody(root.toCompactJson());
    }

    private static boolean objectHasKey(JsonNode root, String parentPath, String key) {
        return root.objectKeysAt(parentPath).contains(key);
    }

    private static String joinPath(String parent, String key) {
        return (parent == null || parent.isEmpty()) ? key : parent + "." + key;
    }

    private static JsonNode parseBodyOrNull(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            return JsonNode.parse(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- cookies

    private static HttpRequest applyCookies(HttpRequest request, List<ParsedField> baseline, List<ParsedField> working) {
        List<ParsedField> baselineCookies = filter(baseline, FieldLocation.COOKIE);
        List<ParsedField> workingCookies = filter(working, FieldLocation.COOKIE);
        if (baselineCookies.isEmpty() && workingCookies.isEmpty()) {
            return request;
        }
        if (workingCookies.isEmpty()) {
            return request.hasHeader("Cookie") ? request.withRemovedHeader("Cookie") : request;
        }
        String rebuilt = workingCookies.stream()
                .map(f -> f.rawKey() + "=" + f.currentValue())
                .collect(Collectors.joining("; "));
        return request.hasHeader("Cookie") ? request.withUpdatedHeader("Cookie", rebuilt) : request.withAddedHeader("Cookie", rebuilt);
    }

    private static String replaceCookiePair(String cookieHeaderValue, String name, String newValue) {
        String[] parts = cookieHeaderValue.split(";\\s*");
        StringBuilder sb = new StringBuilder();
        boolean found = false;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            int eq = part.indexOf('=');
            String key = eq >= 0 ? part.substring(0, eq) : part;
            if (i > 0) {
                sb.append("; ");
            }
            if (key.equals(name)) {
                sb.append(name).append('=').append(newValue);
                found = true;
            } else {
                sb.append(part);
            }
        }
        if (!found) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(name).append('=').append(newValue);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- headers

    private static HttpRequest applyHeaders(HttpRequest request, List<ParsedField> baseline, List<ParsedField> working) {
        List<ParsedField> baselineHeaders = filter(baseline, FieldLocation.HEADER);
        List<ParsedField> workingHeaders = filter(working, FieldLocation.HEADER);

        Set<String> workingNames = workingHeaders.stream().map(f -> f.rawKey().toLowerCase()).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> baselineNames = baselineHeaders.stream().map(f -> f.rawKey().toLowerCase()).collect(Collectors.toCollection(LinkedHashSet::new));

        for (ParsedField f : baselineHeaders) {
            if (!workingNames.contains(f.rawKey().toLowerCase()) && request.hasHeader(f.rawKey())) {
                request = request.withRemovedHeader(f.rawKey());
            }
        }
        for (ParsedField f : workingHeaders) {
            boolean isNew = !baselineNames.contains(f.rawKey().toLowerCase());
            if (isNew) {
                request = request.hasHeader(f.rawKey())
                        ? request.withUpdatedHeader(f.rawKey(), f.currentValue())
                        : request.withAddedHeader(f.rawKey(), f.currentValue());
            } else if (request.hasHeader(f.rawKey())) {
                request = request.withUpdatedHeader(f.rawKey(), f.currentValue());
            }
        }
        return request;
    }

    // ---------------------------------------------------------------- URL / form / multipart params

    private static HttpRequest applyParams(HttpRequest request, List<ParsedField> baseline, List<ParsedField> working,
                                            FieldLocation location, HttpParameterType type) {
        List<ParsedField> baselineParams = filter(baseline, location);
        List<ParsedField> workingParams = filter(working, location);
        if (baselineParams.isEmpty() && workingParams.isEmpty()) {
            return request;
        }

        Set<String> workingNames = workingParams.stream().map(ParsedField::rawKey).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> baselineNames = baselineParams.stream().map(ParsedField::rawKey).collect(Collectors.toCollection(LinkedHashSet::new));

        List<HttpParameter> toRemove = new ArrayList<>();
        for (ParsedField f : baselineParams) {
            if (!workingNames.contains(f.rawKey())) {
                ParsedHttpParameter existing = request.parameter(f.rawKey(), type);
                if (existing != null) {
                    toRemove.add(existing);
                }
            }
        }
        if (!toRemove.isEmpty()) {
            request = request.withRemovedParameters(toRemove);
        }

        List<HttpParameter> toAdd = new ArrayList<>();
        List<HttpParameter> toUpdate = new ArrayList<>();
        for (ParsedField f : workingParams) {
            if (!baselineNames.contains(f.rawKey())) {
                toAdd.add(HttpParameter.parameter(f.rawKey(), f.currentValue(), type));
            } else if (request.hasParameter(f.rawKey(), type)) {
                toUpdate.add(HttpParameter.parameter(f.rawKey(), f.currentValue(), type));
            }
        }
        if (!toAdd.isEmpty()) {
            request = request.withAddedParameters(toAdd);
        }
        if (!toUpdate.isEmpty()) {
            request = request.withUpdatedParameters(toUpdate);
        }
        return request;
    }

    // ---------------------------------------------------------------- XML body (substitution-only, best-effort)

    private static HttpRequest applyXmlBody(HttpRequest request, List<ParsedField> working) {
        List<ParsedField> xmlFields = filter(working, FieldLocation.XML_BODY);
        if (xmlFields.isEmpty()) {
            return request;
        }
        String body = request.bodyToString();
        for (ParsedField f : xmlFields) {
            body = replaceXmlLeaf(body, f.rawKey(), f.currentValue());
        }
        return request.withBody(body);
    }

    private static String replaceXmlLeaf(String body, String tag, String newValue) {
        Pattern p = Pattern.compile(String.format(XML_TAG_TEMPLATE, Pattern.quote(tag)));
        Matcher m = p.matcher(body);
        if (m.find()) {
            return m.replaceFirst(Matcher.quoteReplacement("<" + tag + ">" + newValue + "</" + tag + ">"));
        }
        return body;
    }

    // ---------------------------------------------------------------- helpers

    private static List<ParsedField> onlyRequest(List<ParsedField> fields) {
        return fields.stream().filter(f -> f.direction() == MessageDirection.REQUEST).collect(Collectors.toList());
    }

    private static List<ParsedField> filter(List<ParsedField> fields, FieldLocation location) {
        return fields.stream().filter(f -> f.location() == location).collect(Collectors.toList());
    }
}
