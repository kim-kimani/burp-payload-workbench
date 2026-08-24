package com.cytonn.montoya.payloadextractor.db;

import com.cytonn.montoya.payloadextractor.util.Ids;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A named bucket of remembered {@link PayloadValue}s, keyed by a normalized field name (e.g.
 * "auth token", "otp"). Multiple raw key spellings ("authToken", "auth_token", "Authorization")
 * that normalize to the same name feed into the same collection, which is what lets values
 * captured under one field naming convention be reused against another.
 */
public final class PayloadCollection {

    private final String id;
    private String normalizedName;
    private String category;
    private final List<PayloadValue> values = new ArrayList<>();
    private int activeIndex = -1;

    public PayloadCollection(String id, String normalizedName, String category) {
        this.id = id == null ? Ids.uuid() : id;
        this.normalizedName = normalizedName;
        this.category = category;
    }

    public String id() { return id; }
    public String normalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public String category() { return category; }
    public void setCategory(String category) { this.category = category; }

    public List<PayloadValue> values() { return values; }

    /**
     * Adds {@code value} unless a value with the exact same string is already present, in which
     * case the existing entry's capture time/origin host/source are refreshed instead - this is
     * what keeps repeated "Remember" calls (manual, or the passive listener seeing the same value
     * again) from piling up duplicate rows for one raw value string.
     */
    public PayloadValue add(PayloadValue value) {
        Optional<PayloadValue> existing = values.stream().filter(v -> v.value().equals(value.value())).findFirst();
        if (existing.isPresent()) {
            PayloadValue e = existing.get();
            if (value.capturedAtEpochMillis() > e.capturedAtEpochMillis()) {
                e.setOriginHost(value.originHost());
            }
            return e;
        }
        values.add(value);
        if (activeIndex < 0) {
            activeIndex = 0;
        }
        return value;
    }

    /** Removes duplicate values (same string), keeping the earliest-captured copy of each. Returns how many were removed. */
    public int deduplicate() {
        List<PayloadValue> deduped = new ArrayList<>();
        for (PayloadValue v : values) {
            boolean seen = deduped.stream().anyMatch(d -> d.value().equals(v.value()));
            if (!seen) {
                deduped.add(v);
            }
        }
        int removed = values.size() - deduped.size();
        values.clear();
        values.addAll(deduped);
        if (activeIndex >= values.size()) {
            activeIndex = values.isEmpty() ? -1 : values.size() - 1;
        }
        return removed;
    }

    public boolean remove(String valueId) {
        boolean removed = values.removeIf(v -> v.id().equals(valueId));
        if (activeIndex >= values.size()) {
            activeIndex = values.isEmpty() ? -1 : values.size() - 1;
        }
        return removed;
    }

    public Optional<PayloadValue> find(String valueId) {
        return values.stream().filter(v -> v.id().equals(valueId)).findFirst();
    }

    public Optional<PayloadValue> active() {
        return (activeIndex >= 0 && activeIndex < values.size()) ? Optional.of(values.get(activeIndex)) : Optional.empty();
    }

    public void setActive(String valueId) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).id().equals(valueId)) {
                activeIndex = i;
                return;
            }
        }
    }

    public Optional<PayloadValue> mostRecent() {
        return values.stream().max((a, b) -> Long.compare(a.capturedAtEpochMillis(), b.capturedAtEpochMillis()));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public String toString() {
        return normalizedName + "  [" + category + "]  (" + values.size() + ")";
    }
}
