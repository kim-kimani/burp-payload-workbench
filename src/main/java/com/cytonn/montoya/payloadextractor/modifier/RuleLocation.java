package com.cytonn.montoya.payloadextractor.modifier;

/** Which part of a request/response a {@link ModificationRule}'s find/replace text substitution runs against. */
public enum RuleLocation {
    PATH("Path"),
    QUERY("Query"),
    HEADERS("Headers"),
    COOKIES("Cookies"),
    JSON_BODY("JSON Body"),
    FORM_BODY("Form Data"),
    RAW_BODY("Raw Body"),
    RESPONSE_HEADERS("Response Headers"),
    RESPONSE_BODY("Response Body"),
    ANYWHERE("Anywhere in the message");

    private final String displayName;

    RuleLocation(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isRequestSide() {
        return this != RESPONSE_HEADERS && this != RESPONSE_BODY;
    }

    public boolean isResponseSide() {
        return this == RESPONSE_HEADERS || this == RESPONSE_BODY || this == ANYWHERE;
    }
}
