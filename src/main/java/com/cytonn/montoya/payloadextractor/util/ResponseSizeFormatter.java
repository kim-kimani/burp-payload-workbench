package com.cytonn.montoya.payloadextractor.util;

/**
 * The single human-readable byte-size formatter used everywhere a response size is shown
 * (Intercept, History, Replay, ...) so the display is consistent across the whole extension:
 * "512 B", "1.4 KB", "4.2 KB", "1.2 MB".
 */
public final class ResponseSizeFormatter {

    private ResponseSizeFormatter() {
    }

    public static String format(Long bytes) {
        return bytes == null ? "" : format(bytes.longValue());
    }

    public static String format(long bytes) {
        if (bytes < 0) {
            return "";
        }
        if (bytes < 1000) {
            return bytes + " B";
        }
        if (bytes < 1000 * 1000) {
            return String.format("%.1f KB", bytes / 1000.0);
        }
        return String.format("%.1f MB", bytes / (1000.0 * 1000.0));
    }
}
