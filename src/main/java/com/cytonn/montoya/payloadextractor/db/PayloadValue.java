package com.cytonn.montoya.payloadextractor.db;

import com.cytonn.montoya.payloadextractor.util.Ids;

import java.util.Objects;

/** A single remembered value belonging to a {@link PayloadCollection} (e.g. one captured session token). */
public final class PayloadValue {

    private final String id;
    private String value;
    private PayloadSource source;
    private long capturedAtEpochMillis;
    private String originHost;
    private String notes;
    private boolean favorite;

    public PayloadValue(String id, String value, PayloadSource source, long capturedAtEpochMillis, String originHost, String notes) {
        this(id, value, source, capturedAtEpochMillis, originHost, notes, false);
    }

    public PayloadValue(String id, String value, PayloadSource source, long capturedAtEpochMillis, String originHost, String notes, boolean favorite) {
        this.id = id == null ? Ids.uuid() : id;
        this.value = value == null ? "" : value;
        this.source = source == null ? PayloadSource.MANUAL : source;
        this.capturedAtEpochMillis = capturedAtEpochMillis;
        this.originHost = originHost;
        this.notes = notes;
        this.favorite = favorite;
    }

    public static PayloadValue of(String value, PayloadSource source, long capturedAtEpochMillis, String originHost) {
        return new PayloadValue(null, value, source, capturedAtEpochMillis, originHost, null);
    }

    public String id() { return id; }
    public String value() { return value; }
    public void setValue(String value) { this.value = value == null ? "" : value; }
    public PayloadSource source() { return source; }
    public void setSource(PayloadSource source) { this.source = source; }
    public long capturedAtEpochMillis() { return capturedAtEpochMillis; }
    public String originHost() { return originHost; }
    public void setOriginHost(String originHost) { this.originHost = originHost; }
    public String notes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PayloadValue)) return false;
        return id.equals(((PayloadValue) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return Objects.requireNonNullElse(value, "");
    }
}
