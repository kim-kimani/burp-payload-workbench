package com.cytonn.montoya.payloadextractor.generator;

import java.util.List;

/** A strategy that turns {@link GeneratorParams} into a concrete list of payload value strings. */
public interface PayloadGenerator {

    GeneratorKind kind();

    List<String> generate(GeneratorParams params);

    /** Shared zero-pad-never-truncate formatter for the OTP-style {@code length} option. */
    static String padToLength(long value, Integer length) {
        String s = Long.toString(Math.abs(value));
        if (length == null || length <= 0) {
            return value < 0 ? "-" + s : s;
        }
        if (s.length() >= length) {
            return (value < 0 ? "-" : "") + s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < length; i++) {
            sb.append('0');
        }
        sb.append(s);
        return (value < 0 ? "-" : "") + sb;
    }
}
