package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Produces random strings drawn from {@link GeneratorParams#charset()}, {@link GeneratorParams#length()} characters long (default 8). */
public final class RandomStringGenerator implements PayloadGenerator {

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.RANDOM_STRING;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        int length = (params.length() != null && params.length() > 0) ? params.length() : 8;
        String charset = params.charset();
        Random random = params.randomSeed() != null ? new Random(params.randomSeed()) : new Random();

        if (params.unique()) {
            Set<String> seen = new LinkedHashSet<>();
            long attemptBudget = Math.max(1000L, (long) params.count() * 50L);
            long attempts = 0;
            while (seen.size() < params.count() && attempts < attemptBudget) {
                seen.add(params.prefix() + randomOne(random, charset, length) + params.suffix());
                attempts++;
            }
            return new ArrayList<>(seen);
        }

        List<String> out = new ArrayList<>(Math.max(0, params.count()));
        for (int i = 0; i < params.count(); i++) {
            out.add(params.prefix() + randomOne(random, charset, length) + params.suffix());
        }
        return out;
    }

    private static String randomOne(Random random, String charset, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(random.nextInt(charset.length())));
        }
        return sb.toString();
    }
}
