package com.cytonn.montoya.payloadextractor.parser;

/**
 * Where a {@link ParsedField} physically lives within an HTTP message. This drives which
 * {@code RequestModifier} strategy is used to substitute, add, remove, or reorder the field in
 * the real, wire-level message - not just in the Workbench's on-screen list.
 */
public enum FieldLocation {

    /** A leaf value inside a JSON request or response body. Real add/remove/reorder supported (own JsonNode DOM). */
    JSON_BODY("JSON Body"),

    /** A leaf-ish value inside an XML request or response body (best-effort tag/attribute text). */
    XML_BODY("XML Body"),

    /** A URL query string parameter. Real add/remove supported; wire-order reorder is not (Montoya limitation). */
    URL_PARAM("URL Parameter"),

    /** A URL-encoded body (form) parameter. Real add/remove supported; wire-order reorder is not. */
    FORM_PARAM("Form Parameter"),

    /** A multipart/form-data attribute. Real add/remove supported; wire-order reorder is not. */
    MULTIPART_PARAM("Multipart Parameter"),

    /** A single name=value pair within the Cookie header. Real add/remove/reorder supported (we rebuild the header). */
    COOKIE("Cookie"),

    /** A standalone HTTP header (request or response). Real add/remove supported; wire-order reorder is not. */
    HEADER("Header"),

    /** A value found in a non-JSON, non-form, non-XML raw body (best-effort substring match, substitution only). */
    RAW_BODY("Raw Body");

    private final String displayName;

    FieldLocation(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** True for the two location kinds where a real, wire-level drag-to-reorder is achievable. */
    public boolean supportsRealReorder() {
        return this == JSON_BODY || this == COOKIE;
    }

    /** True for every location kind where a real, wire-level delete/add is achievable (all of them). */
    public boolean supportsRealAddRemove() {
        return true;
    }
}
