package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Produces random integers in {@code [min, max]} (inclusive). When {@link GeneratorParams#length()}
 * is set (OTP use case: "6 digits, random, keep the length"), the range is overridden to the full
 * {@code [0, 10^length - 1]} so the analyst doesn't have to hand-compute a bound like 999999, and
 * every value is zero-padded to that width.
 */
public final class RandomIntegerGenerator implements PayloadGenerator {

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.RANDOM_INTEGER;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        long min = params.min();
        long max = params.max();
        if (params.length() != null && params.length() > 0) {
            min = 0;
            max = pow10(params.length()) - 1;
        }
        if (max < min) {
            long tmp = min; min = max; max = tmp;
        }
        Random random = params.randomSeed() != null ? new Random(params.randomSeed()) : new Random();
        long rangeSize = max - min + 1;
        int targetCount = params.count();

        if (params.unique()) {
            Set<Long> seen = new LinkedHashSet<>();
            long maxPossible = Math.min(targetCount, rangeSize);
            long attempts = 0;
            long attemptBudget = Math.max(1000L, maxPossible * 50L);
            while (seen.size() < maxPossible && attempts < attemptBudget) {
                seen.add(min + (long) (random.nextDouble() * rangeSize));
                attempts++;
            }
            List<String> out = new ArrayList<>();
            for (Long v : seen) {
                out.add(PayloadGenerator.padToLength(v, params.length()));
            }
            return out;
        }

        List<String> out = new ArrayList<>(Math.max(0, targetCount));
        for (int i = 0; i < targetCount; i++) {
            long v = min + (long) (random.nextDouble() * rangeSize);
            out.add(PayloadGenerator.padToLength(v, params.length()));
        }
        return out;
    }

    private static long pow10(int exponent) {
        long result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= 10;
        }
        return result;
    }
}
