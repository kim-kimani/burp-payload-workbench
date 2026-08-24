package com.cytonn.montoya.payloadextractor.util;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central helper for generating unique identifiers used throughout the
 * extension (payload values, history entries, field ids, collections).
 */
public final class Ids {

    private static final AtomicLong COUNTER = new AtomicLong(0);

    private Ids() {
    }

    /** A fully random UUID string, e.g. for payload value / history entry ids. */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** A short, monotonically increasing id, useful for stable UI component ids within a session. */
    public static String next(String prefix) {
        long n = COUNTER.incrementAndGet();
        return (prefix == null || prefix.isEmpty()) ? ("id-" + n) : (prefix + "-" + n);
    }

    /** A short random suffix (8 hex chars) - handy for de-duplicating generated names. */
    public static String shortRandom() {
        String hex = Long.toHexString(Double.doubleToLongBits(Math.random()));
        return hex.length() > 8 ? hex.substring(0, 8) : hex;
    }
}
