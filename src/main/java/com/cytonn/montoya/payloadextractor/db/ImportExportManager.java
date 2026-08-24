package com.cytonn.montoya.payloadextractor.db;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * File-based import/export for the payload collection database, and for plain wordlist files
 * (Intruder-style: one value per line) used by the Replay Payload-From-File feature.
 */
public final class ImportExportManager {

    private ImportExportManager() {
    }

    public static void exportDatabase(PayloadDatabase database, Path target) throws IOException {
        Files.writeString(target, database.toJson(), StandardCharsets.UTF_8);
    }

    public static PayloadDatabase importDatabase(Path source) throws IOException {
        String json = Files.readString(source, StandardCharsets.UTF_8);
        return PayloadDatabase.fromJson(json);
    }

    /** Merges every collection/value from {@code imported} into {@code target}, keeping target's existing values. */
    public static void merge(PayloadDatabase target, PayloadDatabase imported) {
        for (PayloadCollection c : imported.allCollections()) {
            PayloadCollection existing = target.findOrCreate(c.normalizedName(), c.category());
            for (PayloadValue v : c.values()) {
                existing.add(new PayloadValue(null, v.value(), v.source(), v.capturedAtEpochMillis(), v.originHost(), v.notes(), v.isFavorite()));
            }
        }
    }

    /** Exports a single collection's values as a plain one-value-per-line text file (Intruder-wordlist-compatible). */
    public static void exportCollection(PayloadCollection collection, Path target) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (PayloadValue v : collection.values()) {
            sb.append(v.value()).append('\n');
        }
        Files.writeString(target, sb.toString(), StandardCharsets.UTF_8);
    }

    /** Imports plain one-value-per-line text into an existing collection (dedupe-aware, same as any other {@code add}). */
    public static int importValuesIntoCollection(PayloadCollection collection, Path source) throws IOException {
        List<String> values = loadWordlist(source);
        int added = 0;
        for (String v : values) {
            int before = collection.values().size();
            collection.add(PayloadValue.of(v, PayloadSource.MANUAL, System.currentTimeMillis(), null));
            if (collection.values().size() > before) {
                added++;
            }
        }
        return added;
    }

    /**
     * Loads a plain-text wordlist file (one payload value per line; blank lines and lines starting
     * with {@code #} are skipped) for use as a Replay "payload from file" source.
     */
    public static List<String> loadWordlist(Path source) throws IOException {
        List<String> values = new ArrayList<>();
        for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                values.add(trimmed);
            }
        }
        return values;
    }
}
