package com.cytonn.montoya.payloadextractor.intercept;

/** Which phase of an HTTP transaction an {@link InterceptCondition} or {@code ModificationRule} applies to. */
public enum InterceptDirection {
    REQUEST("Request"),
    RESPONSE("Response"),
    BOTH("Both");

    private final String displayName;

    InterceptDirection(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean appliesToRequest() {
        return this == REQUEST || this == BOTH;
    }

    public boolean appliesToResponse() {
        return this == RESPONSE || this == BOTH;
    }
}
