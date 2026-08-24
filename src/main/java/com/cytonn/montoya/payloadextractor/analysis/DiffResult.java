package com.cytonn.montoya.payloadextractor.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * The result of comparing two responses (see {@link ResponseDiff}): what changed and, as a simple
 * heuristic flag for the analyst to look at, whether the change looks worth a second look.
 * Deliberately never claims a vulnerability - {@link #interesting} means "different enough to
 * deserve a manual look", nothing stronger.
 */
public final class DiffResult {

    public static final class FieldChange {
        public final String path;
        public final String oldValue;
        public final String newValue;

        public FieldChange(String path, String oldValue, String newValue) {
            this.path = path;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    public Integer oldStatus;
    public Integer newStatus;
    public boolean statusChanged;

    public Long oldSizeBytes;
    public Long newSizeBytes;
    public long sizeDeltaBytes;
    public boolean sizeChangedSignificantly;

    public final List<String> headersAdded = new ArrayList<>();
    public final List<String> headersRemoved = new ArrayList<>();
    public final List<FieldChange> headersChanged = new ArrayList<>();

    public final List<FieldChange> jsonFieldsAdded = new ArrayList<>();
    public final List<FieldChange> jsonFieldsRemoved = new ArrayList<>();
    public final List<FieldChange> jsonFieldsChanged = new ArrayList<>();

    public boolean bodyDiffersNonJson;

    /** Heuristic-flagged as "worth a second look" - never "vulnerable". See {@link ResponseDiff} for exactly what sets this. */
    public boolean interesting;

    public boolean hasAnyDifference() {
        return statusChanged || sizeChangedSignificantly || !headersAdded.isEmpty() || !headersRemoved.isEmpty()
                || !headersChanged.isEmpty() || !jsonFieldsAdded.isEmpty() || !jsonFieldsRemoved.isEmpty()
                || !jsonFieldsChanged.isEmpty() || bodyDiffersNonJson;
    }

    public String summary() {
        if (!hasAnyDifference()) {
            return "Identical";
        }
        List<String> parts = new ArrayList<>();
        if (statusChanged) parts.add("status " + oldStatus + " -> " + newStatus);
        if (sizeChangedSignificantly) parts.add("size " + oldSizeBytes + "B -> " + newSizeBytes + "B");
        if (!headersAdded.isEmpty()) parts.add(headersAdded.size() + " header(s) added");
        if (!headersRemoved.isEmpty()) parts.add(headersRemoved.size() + " header(s) removed");
        if (!headersChanged.isEmpty()) parts.add(headersChanged.size() + " header(s) changed");
        if (!jsonFieldsAdded.isEmpty()) parts.add(jsonFieldsAdded.size() + " JSON field(s) added");
        if (!jsonFieldsRemoved.isEmpty()) parts.add(jsonFieldsRemoved.size() + " JSON field(s) removed");
        if (!jsonFieldsChanged.isEmpty()) parts.add(jsonFieldsChanged.size() + " JSON field(s) changed");
        if (bodyDiffersNonJson) parts.add("body differs");
        return String.join(", ", parts);
    }
}
