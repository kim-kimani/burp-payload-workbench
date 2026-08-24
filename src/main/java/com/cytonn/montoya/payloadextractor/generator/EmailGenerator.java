package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Produces synthetic-looking email addresses: {@code user<6 random digits>@<domain>}. The domain
 * defaults to {@code example.com}; set {@link GeneratorParams#charset()} to a domain (e.g.
 * {@code mycorp.test}) to override it - reusing the existing params bag rather than adding a new field.
 */
public final class EmailGenerator implements PayloadGenerator {

    private static final String DEFAULT_DOMAIN = "example.com";

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.EMAIL;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        String domain = isCustomDomain(params.charset()) ? params.charset().trim() : DEFAULT_DOMAIN;
        Random random = params.randomSeed() != null ? new Random(params.randomSeed()) : new Random();

        if (params.unique()) {
            Set<String> seen = new LinkedHashSet<>();
            long attemptBudget = Math.max(1000L, (long) params.count() * 50L);
            long attempts = 0;
            while (seen.size() < params.count() && attempts < attemptBudget) {
                seen.add(params.prefix() + "user" + randomDigits(random, 6) + "@" + domain + params.suffix());
                attempts++;
            }
            return new ArrayList<>(seen);
        }

        List<String> out = new ArrayList<>(Math.max(0, params.count()));
        for (int i = 0; i < params.count(); i++) {
            out.add(params.prefix() + "user" + randomDigits(random, 6) + "@" + domain + params.suffix());
        }
        return out;
    }

    /** The params bag's default charset is the big alnum pool used by RANDOM_STRING; only treat it as a real domain override if it looks like one. */
    private static boolean isCustomDomain(String charset) {
        return charset != null && charset.contains(".") && charset.length() < 64 && !charset.contains(" ");
    }

    private static String randomDigits(Random random, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
