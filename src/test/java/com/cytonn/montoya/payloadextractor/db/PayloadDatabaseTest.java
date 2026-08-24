package com.cytonn.montoya.payloadextractor.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadDatabaseTest {

    @Test
    void differentRawKeySpellingsShareOneCollection() {
        PayloadDatabase db = new PayloadDatabase();
        db.remember("authToken", "AUTH_TOKEN", "value-1", PayloadSource.OBSERVED, 1000L, "a.example.com");
        db.remember("auth_token", "AUTH_TOKEN", "value-2", PayloadSource.OBSERVED, 2000L, "b.example.com");

        assertEquals(1, db.collectionCount());
        assertEquals(2, db.valueCount());
    }

    @Test
    void roundTripsThroughJson() {
        PayloadDatabase db = new PayloadDatabase();
        db.remember("otp", "OTP", "654321", PayloadSource.MANUAL, 5000L, "host.example.com");
        db.remember("session", "SESSION_ID", "abc-123", PayloadSource.GENERATED, 6000L, null);

        String json = db.toJson();
        PayloadDatabase restored = PayloadDatabase.fromJson(json);

        assertEquals(db.collectionCount(), restored.collectionCount());
        assertEquals(db.valueCount(), restored.valueCount());
        assertTrue(restored.find("otp").isPresent());
        assertEquals("654321", restored.find("otp").get().values().get(0).value());
    }

    @Test
    void emptyOrNullJsonProducesEmptyDatabase() {
        assertEquals(0, PayloadDatabase.fromJson(null).collectionCount());
        assertEquals(0, PayloadDatabase.fromJson("").collectionCount());
    }

    @Test
    void removeCollectionDeletesItEntirely() {
        PayloadDatabase db = new PayloadDatabase();
        PayloadCollection c = db.findOrCreate("token", "AUTH_TOKEN");
        assertTrue(db.removeCollection(c.id()));
        assertEquals(0, db.collectionCount());
    }
}
