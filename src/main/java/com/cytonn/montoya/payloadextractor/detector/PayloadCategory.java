package com.cytonn.montoya.payloadextractor.detector;

/** Semantic category assigned to a detected field, used for grouping/filtering in the UI and for smarter payload generation defaults. */
public enum PayloadCategory {
    AUTH_TOKEN("Auth Token"),
    SESSION_ID("Session ID"),
    CSRF_TOKEN("CSRF Token"),
    API_KEY("API Key"),
    OTP("OTP / Verification Code"),
    PASSWORD("Password"),
    USERNAME("Username"),
    EMAIL("Email"),
    PHONE_NUMBER("Phone Number"),
    ID_NUMBER("ID / Reference Number"),
    UUID("UUID"),
    TIMESTAMP("Timestamp"),
    AMOUNT("Amount / Currency"),
    GENERIC("Generic");

    private final String displayName;

    PayloadCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
