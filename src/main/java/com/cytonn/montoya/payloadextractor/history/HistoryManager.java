package com.cytonn.montoya.payloadextractor.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-memory, append-only log of {@link HistoryEntry} records for the History panel, newest first. */
public final class HistoryManager {

    private static final int MAX_ENTRIES = 5000;

    private final List<HistoryEntry> entries = new CopyOnWriteArrayList<>();

    public void record(HistoryEntry entry) {
        entries.add(0, entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    public List<HistoryEntry> all() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<HistoryEntry> forField(String fieldName) {
        List<HistoryEntry> out = new ArrayList<>();
        for (HistoryEntry e : entries) {
            if (e.fieldName() != null && e.fieldName().equals(fieldName)) {
                out.add(e);
            }
        }
        return out;
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
