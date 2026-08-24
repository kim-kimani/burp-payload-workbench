package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces consecutive integers starting at {@link GeneratorParams#min()} (default 0). When
 * {@link GeneratorParams#length()} is set (the OTP use case: "6 digits, start from 0, keep the
 * length"), every value is zero-padded to that width - {@code 0, 1, 2, ...} becomes
 * {@code 000000, 000001, 000002, ...}.
 */
public final class SequentialIntegerGenerator implements PayloadGenerator {

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.SEQUENTIAL_INTEGER;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        List<String> out = new ArrayList<>(Math.max(0, params.count()));
        long start = params.min();
        for (int i = 0; i < params.count(); i++) {
            out.add(PayloadGenerator.padToLength(start + i, params.length()));
        }
        return out;
    }
}
