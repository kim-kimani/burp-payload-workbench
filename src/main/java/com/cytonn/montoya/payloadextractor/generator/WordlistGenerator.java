package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * Passes through a fixed list of values (loaded from a remembered {@code PayloadCollection} or an
 * imported wordlist file) - the "reuse what I already captured/imported" generator. If
 * {@link GeneratorParams#count()} exceeds the list size, values cycle; if it's smaller, the list
 * is truncated.
 */
public final class WordlistGenerator implements PayloadGenerator {

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.WORDLIST;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        List<String> source = params.wordlistValues();
        List<String> out = new ArrayList<>();
        if (source.isEmpty()) {
            return out;
        }
        int count = params.count() > 0 ? params.count() : source.size();
        for (int i = 0; i < count; i++) {
            out.add(params.prefix() + source.get(i % source.size()) + params.suffix());
        }
        return out;
    }
}
