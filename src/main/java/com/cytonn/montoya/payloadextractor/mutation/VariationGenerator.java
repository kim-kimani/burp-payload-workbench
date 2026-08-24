package com.cytonn.montoya.payloadextractor.mutation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * "Generate Variations" (item 6): given a field's current value, proposes a small, deterministic
 * set of boundary/neighbor values worth trying - e.g. {@code userId=555} -> {@code 556, 554, 0, -1,
 * ""}. Deliberately reuses the existing Replay infrastructure to actually send/compare them (see
 * {@code ReplayConfigDialog}'s "Smart variations of current value" source option) rather than
 * introducing a second send/compare engine.
 */
public final class VariationGenerator {

    private VariationGenerator() {
    }

    public static List<String> variationsFor(String currentValue) {
        String value = currentValue == null ? "" : currentValue;
        LinkedHashSet<String> out = new LinkedHashSet<>();
        Long numeric = tryParseLong(value);
        if (numeric != null) {
            long v = numeric;
            addIfDifferent(out, value, String.valueOf(v + 1));
            addIfDifferent(out, value, String.valueOf(v - 1));
            addIfDifferent(out, value, "0");
            addIfDifferent(out, value, "-1");
            addIfDifferent(out, value, "");
            addIfDifferent(out, value, "null");
            addIfDifferent(out, value, String.valueOf(Integer.MAX_VALUE));
        } else {
            addIfDifferent(out, value, "");
            addIfDifferent(out, value, "null");
            addIfDifferent(out, value, "0");
            addIfDifferent(out, value, "-1");
            if (!value.isEmpty()) {
                addIfDifferent(out, value, value.toUpperCase());
                addIfDifferent(out, value, value.toLowerCase());
                addIfDifferent(out, value, longBoundaryValue(value));
            }
        }
        return new ArrayList<>(out);
    }

    private static void addIfDifferent(LinkedHashSet<String> out, String original, String candidate) {
        if (!candidate.equals(original)) {
            out.add(candidate);
        }
    }

    /** A long, repeated version of the original value - a simple length-boundary probe. */
    private static String longBoundaryValue(String value) {
        StringBuilder sb = new StringBuilder(value);
        while (sb.length() < 500) {
            sb.append(value);
        }
        return sb.toString();
    }

    private static Long tryParseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
