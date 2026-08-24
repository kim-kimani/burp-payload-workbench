package com.cytonn.montoya.payloadextractor.generator;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Central lookup from {@link GeneratorKind} to the {@link PayloadGenerator} that implements it. */
public final class GeneratorRegistry {

    private final Map<GeneratorKind, PayloadGenerator> generators = new EnumMap<>(GeneratorKind.class);

    public GeneratorRegistry() {
        register(new SequentialIntegerGenerator());
        register(new RandomIntegerGenerator());
        register(new UuidGenerator());
        register(new RandomStringGenerator());
        register(new CustomPatternGenerator());
        register(new RegexGenerator());
        register(new WordlistGenerator());
        register(new CustomScriptGenerator());
    }

    public void register(PayloadGenerator generator) {
        generators.put(generator.kind(), generator);
    }

    public PayloadGenerator get(GeneratorKind kind) {
        PayloadGenerator g = generators.get(kind);
        if (g == null) {
            throw new IllegalArgumentException("No generator registered for " + kind);
        }
        return g;
    }

    public List<String> generate(GeneratorKind kind, GeneratorParams params) {
        return get(kind).generate(params);
    }
}
