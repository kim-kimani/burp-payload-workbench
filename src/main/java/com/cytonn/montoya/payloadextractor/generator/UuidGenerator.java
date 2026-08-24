package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Produces random UUID (v4) strings. */
public final class UuidGenerator implements PayloadGenerator {

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.UUID;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        List<String> out = new ArrayList<>(Math.max(0, params.count()));
        for (int i = 0; i < params.count(); i++) {
            out.add(params.prefix() + UUID.randomUUID() + params.suffix());
        }
        return out;
    }
}
