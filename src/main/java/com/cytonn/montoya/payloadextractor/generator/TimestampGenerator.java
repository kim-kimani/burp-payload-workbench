package com.cytonn.montoya.payloadextractor.generator;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces timestamps for the current moment (each successive value offset by one second so a
 * {@code count > 1} run still yields distinct values). Epoch milliseconds by default; if
 * {@link GeneratorParams#pattern()} is set, it's used as a {@link DateTimeFormatter} pattern
 * (UTC) instead - e.g. {@code yyyy-MM-dd'T'HH:mm:ss'Z'}.
 */
public final class TimestampGenerator implements PayloadGenerator {

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.TIMESTAMP;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        List<String> out = new ArrayList<>(Math.max(0, params.count()));
        long base = System.currentTimeMillis();
        DateTimeFormatter formatter = null;
        if (params.pattern() != null && !params.pattern().isBlank()) {
            try {
                formatter = DateTimeFormatter.ofPattern(params.pattern()).withZone(ZoneOffset.UTC);
            } catch (Exception ignored) {
                formatter = null;
            }
        }
        for (int i = 0; i < params.count(); i++) {
            long millis = base + (i * 1000L);
            String value = formatter != null
                    ? formatter.format(Instant.ofEpochMilli(millis))
                    : String.valueOf(millis);
            out.add(params.prefix() + value + params.suffix());
        }
        return out;
    }
}
