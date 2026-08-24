package com.cytonn.montoya.payloadextractor.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonNodeTest {

    @Test
    void parsesAndPreservesKeyOrder() {
        JsonNode root = JsonNode.parse("{\"b\":1,\"a\":2,\"c\":3}");
        assertEquals(java.util.List.of("b", "a", "c"), root.objectKeysAt(""));
    }

    @Test
    void replacesLeafKeepingNumericType() {
        JsonNode root = JsonNode.parse("{\"otp\":123456}");
        JsonNode updated = root.withReplacedPath("otp", "654321");
        assertEquals("654321", updated.get("otp").asString());
        assertEquals(JsonNode.Type.NUMBER, updated.get("otp").type());
    }

    @Test
    void replacesNestedLeafByDottedPath() {
        JsonNode root = JsonNode.parse("{\"user\":{\"token\":\"abc\"}}");
        JsonNode updated = root.withReplacedPath("user.token", "xyz");
        assertEquals("xyz", updated.get("user").get("token").asString());
    }

    @Test
    void removesKeyEntirely() {
        JsonNode root = JsonNode.parse("{\"a\":1,\"b\":2,\"c\":3}");
        JsonNode updated = root.withRemovedPath("b");
        assertEquals(java.util.List.of("a", "c"), updated.objectKeysAt(""));
        assertFalse(updated.keys().contains("b"));
    }

    @Test
    void removeMissingPathThrows() {
        JsonNode root = JsonNode.parse("{\"a\":1}");
        assertThrows(IllegalArgumentException.class, () -> root.withRemovedPath("nope"));
    }

    @Test
    void addsNewKeyAtSpecificIndex() {
        JsonNode root = JsonNode.parse("{\"a\":1,\"c\":3}");
        JsonNode updated = root.withAddedKey("", "b", "2", 1);
        assertEquals(java.util.List.of("a", "b", "c"), updated.objectKeysAt(""));
        assertEquals("2", updated.get("b").asString());
    }

    @Test
    void addingExistingKeyOverwritesInPlace() {
        JsonNode root = JsonNode.parse("{\"a\":1,\"b\":2,\"c\":3}");
        JsonNode updated = root.withAddedKey("", "b", "99", 0);
        assertEquals(java.util.List.of("a", "b", "c"), updated.objectKeysAt(""));
        assertEquals("99", updated.get("b").asString());
    }

    @Test
    void addsNewKeyToNestedObject() {
        JsonNode root = JsonNode.parse("{\"user\":{\"name\":\"bob\"}}");
        JsonNode updated = root.withAddedKey("user", "otp", "000000", -1);
        assertEquals(java.util.List.of("name", "otp"), updated.objectKeysAt("user"));
    }

    @Test
    void reordersKeyToNewIndex() {
        JsonNode root = JsonNode.parse("{\"a\":1,\"b\":2,\"c\":3}");
        JsonNode updated = root.withReorderedKey("c", 0);
        assertEquals(java.util.List.of("c", "a", "b"), updated.objectKeysAt(""));
    }

    @Test
    void reorderClampsOutOfRangeIndex() {
        JsonNode root = JsonNode.parse("{\"a\":1,\"b\":2,\"c\":3}");
        JsonNode updated = root.withReorderedKey("a", 999);
        assertEquals(java.util.List.of("b", "c", "a"), updated.objectKeysAt(""));
    }

    @Test
    void fullDragReorderSequenceMatchesTargetOrder() {
        // Simulates RequestModifier's "enforce final order" pass: move each key, in target order, to its index.
        JsonNode root = JsonNode.parse("{\"user\":\"bob\",\"otp\":\"111111\",\"session\":\"abc\"}");
        java.util.List<String> targetOrder = java.util.List.of("session", "user", "otp");
        int idx = 0;
        for (String key : targetOrder) {
            root = root.withReorderedKey(key, idx++);
        }
        assertEquals(targetOrder, root.objectKeysAt(""));
    }

    @Test
    void compactJsonRoundTrips() {
        String json = "{\"a\":1,\"b\":\"x\",\"c\":true,\"d\":null,\"e\":[1,2,3]}";
        JsonNode root = JsonNode.parse(json);
        assertEquals(json, root.toCompactJson());
    }

    @Test
    void handlesEscapedStrings() {
        JsonNode root = JsonNode.parse("{\"a\":\"line1\\nline2\\ttab\\\"quote\\\"\"}");
        assertEquals("line1\nline2\ttab\"quote\"", root.get("a").asString());
    }

    @Test
    void arrayIndexPathWorks() {
        JsonNode root = JsonNode.parse("{\"roles\":[\"admin\",\"user\"]}");
        JsonNode updated = root.withReplacedPath("roles[1]", "guest");
        assertEquals("guest", updated.get("roles").get(1).asString());
    }

    @Test
    void parentPathAndLastKeyHelpers() {
        assertEquals("user.roles", JsonNode.parentPathOf("user.roles[0]"));
        assertEquals("user", JsonNode.parentPathOf("user.name"));
        assertEquals("", JsonNode.parentPathOf("name"));
        assertEquals("name", JsonNode.lastKeyOf("user.name"));
        assertTrue(JsonNode.lastKeyOf("roles[0]") == null);
    }
}
