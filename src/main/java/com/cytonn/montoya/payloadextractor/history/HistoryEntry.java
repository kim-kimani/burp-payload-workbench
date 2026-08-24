package com.cytonn.montoya.payloadextractor.history;

import com.cytonn.montoya.payloadextractor.parser.FieldLocation;
import com.cytonn.montoya.payloadextractor.util.Ids;

/**
 * One recorded substitution/action: a field's value changed (edited, generated, replayed with a
 * remembered value, added, or removed) at a point in time. Kept for audit/undo-reference purposes
 * in the History panel.
 */
public final class HistoryEntry {

    public enum Action { VALUE_CHANGED, FIELD_ADDED, FIELD_DUPLICATED, FIELD_REMOVED, FIELD_REORDERED, REPLAY_STEP }

    private final String id;
    private final long timestampEpochMillis;
    private final Action action;
    private final String fieldName;
    private final FieldLocation location;
    private final String oldValue;
    private final String newValue;
    private final String source;
    private final String notes;
    private final String host;
    private final Integer statusCode;

    public HistoryEntry(String id, long timestampEpochMillis, Action action, String fieldName, FieldLocation location,
                         String oldValue, String newValue, String source, String notes) {
        this(id, timestampEpochMillis, action, fieldName, location, oldValue, newValue, source, notes, null, null);
    }

    public HistoryEntry(String id, long timestampEpochMillis, Action action, String fieldName, FieldLocation location,
                         String oldValue, String newValue, String source, String notes, String host, Integer statusCode) {
        this.id = id == null ? Ids.uuid() : id;
        this.timestampEpochMillis = timestampEpochMillis;
        this.action = action;
        this.fieldName = fieldName;
        this.location = location;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.source = source;
        this.notes = notes;
        this.host = host;
        this.statusCode = statusCode;
    }

    public static HistoryEntry of(Action action, String fieldName, FieldLocation location, String oldValue,
                                   String newValue, String source, long nowEpochMillis) {
        return new HistoryEntry(null, nowEpochMillis, action, fieldName, location, oldValue, newValue, source, null, null, null);
    }

    public static HistoryEntry of(Action action, String fieldName, FieldLocation location, String oldValue,
                                   String newValue, String source, long nowEpochMillis, String host, Integer statusCode) {
        return new HistoryEntry(null, nowEpochMillis, action, fieldName, location, oldValue, newValue, source, null, host, statusCode);
    }

    public String id() { return id; }
    public long timestampEpochMillis() { return timestampEpochMillis; }
    public Action action() { return action; }
    public String fieldName() { return fieldName; }
    public FieldLocation location() { return location; }
    public String oldValue() { return oldValue; }
    public String newValue() { return newValue; }
    public String source() { return source; }
    public String notes() { return notes; }
    public String host() { return host; }
    public Integer statusCode() { return statusCode; }

    @Override
    public String toString() {
        return "[" + action + "] " + fieldName + " (" + location + "): " + oldValue + " -> " + newValue;
    }
}
