package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

/** Produces random Base64-encoded strings, each encoding {@link GeneratorParams#length()} random raw bytes (default 12). */
public final class Base64Generator implements PayloadGenerator {

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.BASE64;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        int byteLength = (params.length() != null && params.length() > 0) ? params.length() : 12;
        Random random = params.randomSeed() != null ? new Random(params.randomSeed()) : new Random();
        Base64.Encoder encoder = Base64.getEncoder();

        List<String> out = new ArrayList<>(Math.max(0, params.count()));
        for (int i = 0; i < params.count(); i++) {
            byte[] raw = new byte[byteLength];
            random.nextBytes(raw);
            out.add(params.prefix() + encoder.encodeToString(raw) + params.suffix());
        }
        return out;
    }
}
