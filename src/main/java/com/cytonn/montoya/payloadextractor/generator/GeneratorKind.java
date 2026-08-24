package com.cytonn.montoya.payloadextractor.generator;

/** The available payload generation strategies. */
public enum GeneratorKind {
    SEQUENTIAL_INTEGER("Sequential Integer"),
    RANDOM_INTEGER("Random Integer"),
    UUID("UUID"),
    RANDOM_STRING("Random String"),
    CUSTOM_PATTERN("Custom Pattern"),
    REGEX("Regex-based"),
    WORDLIST("Wordlist / Collection"),
    CUSTOM_SCRIPT("Custom Script");

    private final String displayName;

    GeneratorKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
