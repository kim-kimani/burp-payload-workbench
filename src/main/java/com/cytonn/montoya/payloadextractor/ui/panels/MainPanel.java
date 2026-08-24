package com.cytonn.montoya.payloadextractor.ui.panels;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.ui.SettingsPanel;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The extension's Burp suite tab: a top bar (view-only scope filter for Collections/History) over
 * Workbench / Payload Collections / History / AI Assistant / Settings. There is deliberately no
 * standalone "Scope" tab in v1.4.0 - the include/exclude host patterns and the passive-learning
 * master switch live in Settings -> General, and this top bar's "Show only in-scope items" /
 * "Target" controls are a separate, purely visual filter over Burp's own real target scope.
 */
public final class MainPanel extends JPanel {

    private final ExtensionState state;
    private final JTabbedPane tabs = new JTabbedPane();
    private final InterceptPanel interceptPanel;
    private final WorkbenchPanel workbenchPanel;
    private final CollectionsPanel collectionsPanel;
    private final HistoryPanel historyPanel;

    private final JCheckBox scopeOnlyCheckBox = new JCheckBox("Show only in-scope items");
    private final JComboBox<String> targetCombo = new JComboBox<>();

    public MainPanel(ExtensionState state) {
        super(new BorderLayout());
        this.state = state;

        interceptPanel = new InterceptPanel(state);
        workbenchPanel = new WorkbenchPanel(state);
        collectionsPanel = new CollectionsPanel(state);
        historyPanel = new HistoryPanel(state);
        AiPanel aiPanel = new AiPanel(state);
        SettingsPanel settingsPanel = new SettingsPanel(state);

        add(buildTopBar(), BorderLayout.NORTH);

        tabs.addTab("Intercept", interceptPanel);
        tabs.addTab("Workbench", workbenchPanel);
        tabs.addTab("Payload Collections", collectionsPanel);
        tabs.addTab("History", historyPanel);
        tabs.addTab("AI Assistant", aiPanel);
        tabs.addTab("Settings", settingsPanel);

        tabs.addChangeListener(e -> {
            Component selected = tabs.getSelectedComponent();
            if (selected == collectionsPanel) {
                refreshTargetOptions();
                collectionsPanel.refreshCollectionList();
            } else if (selected == historyPanel) {
                refreshTargetOptions();
                historyPanel.refresh();
            }
        });

        add(tabs, BorderLayout.CENTER);

        state.setMainPanel(this);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel();
        bar.setLayout(new BoxLayout(bar, BoxLayout.Y_AXIS));
        bar.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        scopeOnlyCheckBox.addActionListener(e -> {
            state.setViewInScopeOnly(scopeOnlyCheckBox.isSelected());
            targetCombo.setEnabled(scopeOnlyCheckBox.isSelected());
            applyFilterChange();
        });
        row.add(scopeOnlyCheckBox);

        row.add(new JLabel("Target:"));
        targetCombo.setEditable(false);
        targetCombo.setEnabled(false);
        targetCombo.addItem("Any in-scope host");
        targetCombo.addActionListener(e -> {
            state.setViewTargetHost((String) targetCombo.getSelectedItem());
            applyFilterChange();
        });
        row.add(targetCombo);
        bar.add(row);

        JLabel helper = new JLabel("Reduces clutter in Payload Collections and History; nothing is deleted.");
        helper.setFont(helper.getFont().deriveFont(Font.ITALIC, helper.getFont().getSize2D() - 1f));
        helper.setForeground(new Color(120, 120, 120));
        helper.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 0));
        bar.add(helper);

        bar.add(new JSeparator());
        return bar;
    }

    private void applyFilterChange() {
        collectionsPanel.refreshCollectionList();
        historyPanel.refresh();
    }

    /** Rebuilds the Target dropdown's host list from everything currently known in Collections + History. */
    private void refreshTargetOptions() {
        Object previous = targetCombo.getSelectedItem();
        Set<String> hosts = new LinkedHashSet<>();
        state.database().allCollections().forEach(c -> c.values().forEach(v -> {
            if (v.originHost() != null && !v.originHost().isBlank()) {
                hosts.add(v.originHost());
            }
        }));
        state.historyManager().all().forEach(h -> {
            if (h.host() != null && !h.host().isBlank()) {
                hosts.add(h.host());
            }
        });
        targetCombo.removeAllItems();
        targetCombo.addItem("Any in-scope host");
        for (String host : hosts) {
            targetCombo.addItem(host);
        }
        if (previous != null) {
            targetCombo.setSelectedItem(previous);
        }
    }

    /** The Workbench panel - the AI Assistant tab reads the currently-loaded request/response and field list from it. */
    public WorkbenchPanel workbenchPanel() {
        return workbenchPanel;
    }

    /** Loads a request/response into the Workbench and switches to it - the target of the context-menu action and any future "open" entry points. */
    public void openInWorkbench(HttpRequestResponse requestResponse) {
        workbenchPanel.openInWorkbench(requestResponse);
        tabs.setSelectedComponent(workbenchPanel);
    }

    /** Loads a request/response into the Workbench and immediately opens Replay for its first interesting field - the target of Intercept's "Send to Replay". */
    public void openInWorkbenchAndReplay(HttpRequestResponse requestResponse) {
        workbenchPanel.openInWorkbenchAndReplay(requestResponse);
        tabs.setSelectedComponent(workbenchPanel);
    }
}
