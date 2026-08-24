package com.cytonn.montoya.payloadextractor;

import burp.api.montoya.MontoyaApi;
import com.cytonn.montoya.payloadextractor.ai.AiSettings;
import com.cytonn.montoya.payloadextractor.db.PayloadDatabase;
import com.cytonn.montoya.payloadextractor.db.PersistenceManager;
import com.cytonn.montoya.payloadextractor.db.ScopeFilter;
import com.cytonn.montoya.payloadextractor.generator.GeneratorRegistry;
import com.cytonn.montoya.payloadextractor.history.HistoryManager;
import com.cytonn.montoya.payloadextractor.ui.panels.MainPanel;

/**
 * The extension's single shared state object: the live {@link MontoyaApi} handle, the persistent
 * payload database, scope filter, history log, AI settings, and generator registry. One instance
 * is created in {@link PayloadExtractorExtension#initialize} and threaded through every UI panel
 * and background listener, so there is exactly one source of truth for "what has this extension
 * observed/remembered so far".
 */
public final class ExtensionState {

    private final MontoyaApi api;
    private final PersistenceManager persistenceManager;
    private final PayloadDatabase database;
    private final ScopeFilter scopeFilter;
    private final HistoryManager historyManager;
    private final GeneratorRegistry generatorRegistry;
    private AiSettings aiSettings;

    private MainPanel mainPanel;

    /** Transient (not persisted) view-only filter for the top bar: "Show only in-scope items" + "Target: ...". */
    private boolean viewInScopeOnly = false;
    private String viewTargetHost = null; // null/"" = any in-scope host

    public ExtensionState(MontoyaApi api) {
        this.api = api;
        this.persistenceManager = new PersistenceManager(api.persistence().extensionData());
        this.database = persistenceManager.loadDatabase();
        this.scopeFilter = persistenceManager.loadScopeFilter();
        this.historyManager = new HistoryManager();
        this.generatorRegistry = new GeneratorRegistry();
        this.aiSettings = AiSettings.fromJson(persistenceManager.loadRawAiSettingsJson());
    }

    public MontoyaApi api() { return api; }
    public PersistenceManager persistenceManager() { return persistenceManager; }
    public PayloadDatabase database() { return database; }
    public ScopeFilter scopeFilter() { return scopeFilter; }
    public HistoryManager historyManager() { return historyManager; }
    public GeneratorRegistry generatorRegistry() { return generatorRegistry; }

    public AiSettings aiSettings() { return aiSettings; }
    public void setAiSettings(AiSettings aiSettings) {
        this.aiSettings = aiSettings;
        persistenceManager.saveRawAiSettingsJson(aiSettings.toJson());
    }

    public MainPanel mainPanel() { return mainPanel; }
    public void setMainPanel(MainPanel mainPanel) { this.mainPanel = mainPanel; }

    public boolean isViewInScopeOnly() { return viewInScopeOnly; }
    public void setViewInScopeOnly(boolean viewInScopeOnly) { this.viewInScopeOnly = viewInScopeOnly; }

    public String viewTargetHost() { return viewTargetHost; }
    public void setViewTargetHost(String viewTargetHost) { this.viewTargetHost = viewTargetHost; }

    /**
     * View-only visibility check used by Payload Collections/History to reduce clutter - never
     * deletes or hides data from the underlying database, only from what's displayed. Uses Burp's
     * real target scope ({@code api.scope().isInScope(...)}) rather than this extension's own
     * include/exclude glob patterns, since the top-bar "Target" control is meant to mirror Burp's
     * actual defined scope.
     */
    public boolean isHostVisible(String host) {
        if (!viewInScopeOnly) {
            return true;
        }
        if (host == null || host.isBlank()) {
            return false;
        }
        boolean inScope;
        try {
            inScope = api.scope().isInScope("https://" + host + "/");
        } catch (Exception e) {
            inScope = true;
        }
        if (!inScope) {
            return false;
        }
        if (viewTargetHost != null && !viewTargetHost.isBlank() && !"Any in-scope host".equals(viewTargetHost)) {
            return host.equalsIgnoreCase(viewTargetHost);
        }
        return true;
    }

    /** Persists everything that isn't already saved eagerly on each mutation (called on unload, as a safety net). */
    public void persistAll() {
        persistenceManager.saveDatabase(database);
        persistenceManager.saveScopeFilter(scopeFilter);
        persistenceManager.saveRawAiSettingsJson(aiSettings.toJson());
    }
}
