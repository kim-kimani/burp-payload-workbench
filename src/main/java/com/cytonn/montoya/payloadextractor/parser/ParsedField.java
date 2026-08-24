package com.cytonn.montoya.payloadextractor.parser;

import com.cytonn.montoya.payloadextractor.util.Ids;

import java.util.Objects;

/**
 * A single field/value found (or manually added/duplicated) within an HTTP request or response -
 * the unit the Workbench displays as a draggable box and the unit {@code RequestModifier} knows
 * how to locate and mutate in the real, wire-level message.
 *
 * <p>Identity ({@link #id()}) is stable for the lifetime of a Workbench session, independent of
 * the field's current value, name, or position - this is what lets drag-reorder and the "X" remove
 * button target the right field even after edits.
 */
public final class ParsedField {

    private final String id;
    private final FieldLocation location;
    private final MessageDirection direction;

    /** The raw key/parameter/header/cookie name exactly as it appears in the message. */
    private final String rawKey;

    /**
     * Locator used by {@code RequestModifier}: for JSON_BODY this is the full JsonNode path
     * (e.g. {@code user.credentials.otp}); for every other location it is simply the rawKey.
     */
    private final String path;

    /** For COOKIE fields, always "Cookie". For HEADER fields, the header's own name. Null otherwise. */
    private final String headerName;

    private final String originalValue;

    /** True if this field was created via Add/Duplicate rather than auto-detected. */
    private final boolean manuallyAdded;

    private String name;
    private String currentValue;
    private boolean enabled = true;
    private String category = "GENERIC";

    private ParsedField(String id, FieldLocation location, MessageDirection direction, String rawKey,
                         String path, String headerName, String originalValue, boolean manuallyAdded,
                         String name) {
        this.id = id;
        this.location = location;
        this.direction = direction;
        this.rawKey = rawKey;
        this.path = path;
        this.headerName = headerName;
        this.originalValue = originalValue;
        this.manuallyAdded = manuallyAdded;
        this.name = name;
        this.currentValue = originalValue;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id = Ids.uuid();
        private FieldLocation location;
        private MessageDirection direction = MessageDirection.REQUEST;
        private String rawKey;
        private String path;
        private String headerName;
        private String originalValue = "";
        private boolean manuallyAdded = false;
        private String name;

        public Builder id(String id) { this.id = id; return this; }
        public Builder location(FieldLocation location) { this.location = location; return this; }
        public Builder direction(MessageDirection direction) { this.direction = direction; return this; }
        public Builder rawKey(String rawKey) { this.rawKey = rawKey; return this; }
        public Builder path(String path) { this.path = path; return this; }
        public Builder headerName(String headerName) { this.headerName = headerName; return this; }
        public Builder originalValue(String originalValue) { this.originalValue = originalValue == null ? "" : originalValue; return this; }
        public Builder manuallyAdded(boolean manuallyAdded) { this.manuallyAdded = manuallyAdded; return this; }
        public Builder name(String name) { this.name = name; return this; }

        public ParsedField build() {
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(rawKey, "rawKey");
            if (path == null) {
                path = rawKey;
            }
            if (name == null) {
                name = rawKey;
            }
            return new ParsedField(id, location, direction, rawKey, path, headerName, originalValue, manuallyAdded, name);
        }
    }

    /** Returns a deep-enough copy suitable for a "Duplicate" action: fresh id, same everything else, marked manually-added. */
    public ParsedField duplicate() {
        return builder()
                .location(location)
                .direction(direction)
                .rawKey(rawKey)
                .path(path)
                .headerName(headerName)
                .originalValue(currentValue)
                .manuallyAdded(true)
                .name(name + " (copy)")
                .build();
    }

    /** A fresh, independently-mutable copy for the Workbench's working list - same slot identity (location/path/rawKey), fresh id, not flagged as manually-added even if the source was. */
    public ParsedField copyForWorking() {
        ParsedField c = builder()
                .location(location)
                .direction(direction)
                .rawKey(rawKey)
                .path(path)
                .headerName(headerName)
                .originalValue(originalValue)
                .manuallyAdded(manuallyAdded)
                .name(name)
                .build();
        c.setCurrentValue(currentValue);
        c.setEnabled(enabled);
        c.setCategory(category);
        return c;
    }

    public String id() { return id; }
    public FieldLocation location() { return location; }
    public MessageDirection direction() { return direction; }
    public String rawKey() { return rawKey; }
    public String path() { return path; }
    public String headerName() { return headerName; }
    public String originalValue() { return originalValue; }
    public boolean manuallyAdded() { return manuallyAdded; }

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public String currentValue() { return currentValue; }
    public void setCurrentValue(String currentValue) { this.currentValue = currentValue == null ? "" : currentValue; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String category() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isDirty() {
        return !Objects.equals(originalValue, currentValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParsedField)) return false;
        return id.equals(((ParsedField) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ParsedField{" + name + " @ " + location + "[" + path + "] = " + currentValue + "}";
    }
}
