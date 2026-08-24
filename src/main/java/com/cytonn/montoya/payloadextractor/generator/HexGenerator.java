package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Produces random lowercase hex strings, {@link GeneratorParams#length()} characters long (default 16, i.e. 8 random bytes). */
public final class HexGenerator implements PayloadGenerator {

    private static final String HEX_CHARS = "0123456789abcdef";

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.HEX;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        int length = (params.length() != null && params.length() > 0) ? params.length() : 16;
        Random random = params.randomSeed() != null ? new Random(params.randomSeed()) : new Random();

        if (params.unique()) {
            Set<String> seen = new LinkedHashSet<>();
            long attemptBudget = Math.max(1000L, (long) params.count() * 50L);
            long attempts = 0;
            while (seen.size() < params.count() && attempts < attemptBudget) {
                seen.add(params.prefix() + randomHex(random, length) + params.suffix());
                attempts++;
            }
            return new ArrayList<>(seen);
        }

        List<String> out = new ArrayList<>(Math.max(0, params.count()));
        for (int i = 0; i < params.count(); i++) {
            out.add(params.prefix() + randomHex(random, length) + params.suffix());
        }
        return out;
    }

    private static String randomHex(Random random, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(HEX_CHARS.charAt(random.nextInt(HEX_CHARS.length())));
        }
        return sb.toString();
    }
}
