package com.cytonn.montoya.payloadextractor.generator;

import com.cytonn.montoya.payloadextractor.script.ScriptEngineManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Type what I want" generator: compiles {@link GeneratorParams#customScript()} once, then invokes
 * it once per value with {@code index}/{@code count} bound - see {@link ScriptEngineManager} for
 * the supported script language.
 */
public final class CustomScriptGenerator implements PayloadGenerator {

    private String collectionNameHint = "";

    public CustomScriptGenerator() {
    }

    public CustomScriptGenerator withCollectionNameHint(String hint) {
        this.collectionNameHint = hint == null ? "" : hint;
        return this;
    }

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.CUSTOM_SCRIPT;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        List<String> out = new ArrayList<>(Math.max(0, params.count()));
        ScriptEngineManager.CompiledUserScript compiled = ScriptEngineManager.prepare(params.customScript());
        for (int i = 0; i < params.count(); i++) {
            Map<String, Object> context = new HashMap<>();
            context.put("collectionName", collectionNameHint);
            context.put("count", params.count());
            String value = compiled.invoke(i, params.count(), context);
            out.add(params.prefix() + value + params.suffix());
        }
        return out;
    }
}
