package com.cytonn.montoya.payloadextractor.db;

import burp.api.montoya.persistence.PersistedObject;

/**
 * Bridges {@link PayloadDatabase} to Burp's built-in extension-data persistence
 * ({@code PersistedObject}), so the collection database survives across Burp restarts without any
 * external storage. The whole database is serialized to one JSON string (see
 * {@link PayloadDatabase#toJson()}) rather than one persisted key per value, since Montoya's
 * persistence API has no notion of nested structured objects for our own domain types.
 */
public final class PersistenceManager {

    private static final String KEY_DATABASE_JSON = "payloadDatabaseJson";
    private static final String KEY_SCOPE_JSON = "scopeFilterJson";
    private static final String KEY_AI_SETTINGS_JSON = "aiSettingsJson";

    private final PersistedObject store;

    public PersistenceManager(PersistedObject store) {
        this.store = store;
    }

    public PayloadDatabase loadDatabase() {
        try {
            String json = store.getString(KEY_DATABASE_JSON);
            return PayloadDatabase.fromJson(json);
        } catch (Exception e) {
            return new PayloadDatabase();
        }
    }

    public void saveDatabase(PayloadDatabase database) {
        store.setString(KEY_DATABASE_JSON, database.toJson());
    }

    public ScopeFilter loadScopeFilter() {
        try {
            String json = store.getString(KEY_SCOPE_JSON);
            return ScopeFilter.fromJson(json);
        } catch (Exception e) {
            return new ScopeFilter();
        }
    }

    public void saveScopeFilter(ScopeFilter filter) {
        store.setString(KEY_SCOPE_JSON, filter.toJson());
    }

    public String loadRawAiSettingsJson() {
        return store.getString(KEY_AI_SETTINGS_JSON);
    }

    public void saveRawAiSettingsJson(String json) {
        store.setString(KEY_AI_SETTINGS_JSON, json);
    }
}
