package com.cytonn.montoya.payloadextractor.detector;

import java.util.regex.Pattern;

/**
 * Turns a raw key (snake_case, kebab-case, camelCase, SCREAMING_CASE, dotted/bracketed JSON
 * paths, ...) into a clean, human-readable display name, and provides the lower/alnum-only
 * "normal form" used to match keys against {@link InterestingKeyMatcher} regardless of casing
 * convention.
 */
public final class NameNormalizer {

    private static final Pattern ARRAY_INDEX = Pattern.compile("\\[\\d+]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])");

    private NameNormalizer() {
    }

    /** Lowercase, punctuation-stripped, camelCase-split "normal form" - e.g. "authToken" and "auth_token" both become "auth token". */
    public static String normalForm(String rawKeyOrPath) {
        if (rawKeyOrPath == null || rawKeyOrPath.isEmpty()) {
            return "";
        }
        String s = lastSegment(rawKeyOrPath);
        s = CAMEL_BOUNDARY.matcher(s).replaceAll(" ");
        s = s.toLowerCase();
        s = NON_ALNUM.matcher(s).replaceAll(" ");
        return s.trim().replaceAll("\\s+", " ");
    }

    /** A friendly Title Case display name derived from the raw key/path, e.g. "user.otpCode" -> "Otp Code". */
    public static String displayName(String rawKeyOrPath) {
        String normal = normalForm(rawKeyOrPath);
        if (normal.isEmpty()) {
            return rawKeyOrPath == null ? "" : rawKeyOrPath;
        }
        String[] words = normal.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.length() > 1 ? w.substring(1) : "");
        }
        return sb.toString();
    }

    /** For a dotted/bracketed JSON path like {@code user.tokens[0].value}, returns just the final key ("value"). */
    public static String lastSegment(String pathOrKey) {
        String s = ARRAY_INDEX.matcher(pathOrKey).replaceAll("");
        int dot = s.lastIndexOf('.');
        return dot >= 0 ? s.substring(dot + 1) : s;
    }
}
