package com.cytonn.montoya.payloadextractor.db;

/** How a {@link PayloadValue} entered the collection database. */
public enum PayloadSource {
    /** Auto-detected while observing live traffic. */
    OBSERVED("Observed in traffic"),
    /** Typed or edited by hand in the Workbench/Collections UI. */
    MANUAL("Manually entered"),
    /** Produced by a {@code PayloadGenerator}. */
    GENERATED("Generated"),
    /** Suggested by the DeepSeek AI integration. */
    AI_SUGGESTED("AI suggested"),
    /** Brought in via file import (collection JSON import, or a replay wordlist file). */
    IMPORTED("Imported");

    private final String displayName;

    PayloadSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
