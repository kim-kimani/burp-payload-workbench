package com.cytonn.montoya.payloadextractor.variables;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The extension-wide set of named {@link Variable}s - "extract as USER_ID" writes here, and
 * {@link VariableResolver} reads from here to substitute {@code {{USER_ID}}} in later requests.
 * Thread-safe (extraction can happen from a UI action while a background replay/intercept thread
 * resolves at the same time), following the same live-list convention as
 * {@code RuleEngine.rules()}/{@code InterceptEngine.conditions()}.
 */
public final class VariableStore {

    private final List<Variable> variables = new CopyOnWriteArrayList<>();

    public List<Variable> all() {
        return variables;
    }

    public Optional<Variable> find(String name) {
        String normalized = Variable.normalizeName(name);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return variables.stream().filter(v -> v.name().equals(normalized)).findFirst();
    }

    /** The variable's current value, or {@code null} if no variable by that name exists. */
    public String get(String name) {
        return find(name).map(Variable::value).orElse(null);
    }

    /** Creates a new variable or updates the existing one with the same (normalized) name - the "extract as X" operation. */
    public Variable upsert(String name, String value, String sourceHost) {
        Optional<Variable> existing = find(name);
        if (existing.isPresent()) {
            Variable v = existing.get();
            v.setValue(value);
            v.setSourceHost(sourceHost);
            v.setUpdatedEpochMillis(System.currentTimeMillis());
            return v;
        }
        Variable created = new Variable(null, name, value, sourceHost, System.currentTimeMillis());
        variables.add(created);
        return created;
    }

    public boolean remove(String id) {
        return variables.removeIf(v -> v.id().equals(id));
    }

    public void clear() {
        variables.clear();
    }

    // ---------------------------------------------------------------- persistence

    public String toJson() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < variables.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(variables.get(i).toJson());
        }
        return sb.append(']').toString();
    }

    public static VariableStore fromJson(String json) {
        VariableStore store = new VariableStore();
        if (json == null || json.isBlank()) {
            return store;
        }
        try {
            com.cytonn.montoya.payloadextractor.util.JsonNode root = com.cytonn.montoya.payloadextractor.util.JsonNode.parse(json);
            if (root.isArray()) {
                for (int i = 0; i < root.size(); i++) {
                    store.variables.add(Variable.fromJson(root.get(i)));
                }
            }
        } catch (Exception ignored) {
        }
        return store;
    }
}
