package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Produces synthetic E.164-style phone numbers: {@code +1} followed by 10 random digits. */
public final class PhoneGenerator implements PayloadGenerator {

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.PHONE;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        Random random = params.randomSeed() != null ? new Random(params.randomSeed()) : new Random();

        if (params.unique()) {
            Set<String> seen = new LinkedHashSet<>();
            long attemptBudget = Math.max(1000L, (long) params.count() * 50L);
            long attempts = 0;
            while (seen.size() < params.count() && attempts < attemptBudget) {
                seen.add(params.prefix() + "+1" + randomDigits(random, 10) + params.suffix());
                attempts++;
            }
            return new ArrayList<>(seen);
        }

        List<String> out = new ArrayList<>(Math.max(0, params.count()));
        for (int i = 0; i < params.count(); i++) {
            out.add(params.prefix() + "+1" + randomDigits(random, 10) + params.suffix());
        }
        return out;
    }

    private static String randomDigits(Random random, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
