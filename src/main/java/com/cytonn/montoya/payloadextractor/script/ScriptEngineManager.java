package com.cytonn.montoya.payloadextractor.script;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Prepares a Custom Script generator's source once ({@link #prepare(String)}) and returns a
 * {@link CompiledUserScript} that can be invoked many times cheaply - important for generating
 * hundreds of payload values without re-parsing the script on every call.
 *
 * <p>If a JSR-223 JavaScript engine happens to be present on the running JVM (e.g. a Burp
 * installation whose bundled JRE still carries Nashorn, or one with {@code nashorn-core} added to
 * its module path), real JavaScript is used and the script must define a
 * {@code function generate(index, count, context)} returning the payload string. Since the JDK
 * has shipped no script engine by default since Nashorn's removal (Java 15+), and most current
 * Burp installations are no exception, this transparently falls back to {@link MiniScriptEngine} -
 * a small dependency-free scripting language documented on that class - so the feature always
 * works out of the box.
 */
public final class ScriptEngineManager {

    private ScriptEngineManager() {
    }

    public interface CompiledUserScript {
        String invoke(int index, int count, Map<String, Object> context);
    }

    public static CompiledUserScript prepare(String source) {
        CompiledUserScript jsr223 = tryPrepareJsr223(source);
        if (jsr223 != null) {
            return jsr223;
        }
        MiniScriptEngine.Program program = MiniScriptEngine.compile(source);
        return (index, count, context) -> {
            Map<String, Object> bindings = new HashMap<>();
            bindings.put("index", (double) index);
            bindings.put("count", (double) count);
            if (context != null) {
                bindings.putAll(context);
            }
            return program.run(bindings, new Random());
        };
    }

    private static CompiledUserScript tryPrepareJsr223(String source) {
        try {
            javax.script.ScriptEngineManager mgr = new javax.script.ScriptEngineManager();
            ScriptEngine engine = firstAvailable(mgr, "JavaScript", "js", "nashorn", "graal.js");
            if (engine == null || !(engine instanceof Compilable) || !(engine instanceof Invocable)) {
                return null;
            }
            CompiledScript compiled = ((Compilable) engine).compile(source);
            Invocable invocable = (Invocable) engine;
            return (index, count, context) -> {
                try {
                    compiled.eval();
                    Object result = invocable.invokeFunction("generate", index, count, context == null ? Map.of() : context);
                    return result == null ? "" : String.valueOf(result);
                } catch (Exception e) {
                    return "";
                }
            };
        } catch (Throwable t) {
            return null;
        }
    }

    private static ScriptEngine firstAvailable(javax.script.ScriptEngineManager mgr, String... names) {
        for (String name : names) {
            ScriptEngine engine = mgr.getEngineByName(name);
            if (engine != null) {
                return engine;
            }
        }
        return null;
    }
}
