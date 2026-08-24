package com.cytonn.montoya.payloadextractor.variables;

import com.cytonn.montoya.payloadextractor.util.Ids;
import com.cytonn.montoya.payloadextractor.util.JsonNode;

/**
 * One named, stored value usable as {@code {{NAME}}} in future requests - e.g. extract
 * {@code user_id: 345} from a login response as {@code USER_ID}, then type
 * {@code /api/users/{{USER_ID}}} into a later request and it resolves at send time
 * (see {@link VariableResolver}). Names are stored normalized (upper snake case) so lookups are
 * case-insensitive and forgiving of spaces/punctuation typed by hand.
 */
public final class Variable {

    private final String id;
    private String name;
    private String value;
    private String sourceHost;
    private long updatedEpochMillis;
    private String notes = "";

    public Variable(String id, String name, String value, String sourceHost, long updatedEpochMillis) {
        this.id = id == null ? Ids.uuid() : id;
        this.name = normalizeName(name);
        this.value = value == null ? "" : value;
        this.sourceHost = sourceHost;
        this.updatedEpochMillis = updatedEpochMillis;
    }

    public static String normalizeName(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_]+", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        return cleaned;
    }

    public String id() { return id; }
    public String name() { return name; }
    public void setName(String name) { this.name = normalizeName(name); }
    public String value() { return value; }
    public void setValue(String value) { this.value = value == null ? "" : value; }
    public String sourceHost() { return sourceHost; }
    public void setSourceHost(String sourceHost) { this.sourceHost = sourceHost; }
    public long updatedEpochMillis() { return updatedEpochMillis; }
    public void setUpdatedEpochMillis(long updatedEpochMillis) { this.updatedEpochMillis = updatedEpochMillis; }
    public String notes() { return notes; }
    public void setNotes(String notes) { this.notes = notes == null ? "" : notes; }

    // ---------------------------------------------------------------- persistence

    public String toJson() {
        return "{\"id\":" + q(id) + ",\"name\":" + q(name) + ",\"value\":" + q(value)
                + ",\"sourceHost\":" + q(sourceHost) + ",\"updatedEpochMillis\":" + updatedEpochMillis
                + ",\"notes\":" + q(notes) + "}";
    }

    public static Variable fromJson(JsonNode n) {
        String id = str(n.get("id"));
        String name = str(n.get("name"));
        String value = str(n.get("value"));
        String sourceHost = str(n.get("sourceHost"));
        long updated = 0L;
        JsonNode updatedNode = n.get("updatedEpochMillis");
        if (updatedNode != null && !updatedNode.isNull()) {
            try { updated = Long.parseLong(updatedNode.asString()); } catch (NumberFormatException ignored) { }
        }
        Variable v = new Variable(id, name, value, sourceHost, updated);
        String notes = str(n.get("notes"));
        if (notes != null) {
            v.setNotes(notes);
        }
        return v;
    }

    private static String str(JsonNode n) { return n == null || n.isNull() ? null : n.asString(); }

    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                default: sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    @Override
    public String toString() {
        return "{{" + name + "}} = " + value;
    }
}
