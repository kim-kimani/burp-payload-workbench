package com.cytonn.montoya.payloadextractor.ui;

import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.ai.AiSettings;
import com.cytonn.montoya.payloadextractor.ai.DeepSeekClient;

import javax.swing.*;
import java.awt.*;

/**
 * "Settings": DeepSeek AI Configuration, then General (the passive-learning master switch, plus an
 * Advanced link to the host include/exclude pattern editor - folded in here rather than as its own
 * tab, per the v1.4.0 layout). Save Settings persists everything on this page; Test Connection fires
 * a minimal request at the configured endpoint without touching any target traffic.
 */
public final class SettingsPanel extends JPanel {

    public SettingsPanel(ExtensionState state) {
        super(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        AiSettings ai = state.aiSettings();

        JPanel aiPanel = new JPanel();
        aiPanel.setLayout(new BoxLayout(aiPanel, BoxLayout.Y_AXIS));
        aiPanel.setBorder(BorderFactory.createTitledBorder("DeepSeek AI Configuration"));

        JCheckBox enabledBox = new JCheckBox("Enable AI-suggested payload values", ai.isEnabled());
        JTextField endpointField = new JTextField(ai.endpoint(), 30);
        JTextField apiKeyField = new JPasswordField(ai.apiKey(), 30);
        JTextField modelField = new JTextField(ai.model(), 20);
        JSpinner timeoutSpinner = new JSpinner(new SpinnerNumberModel(ai.timeoutSeconds(), 5, 600, 5));
        JSpinner temperatureSpinner = new JSpinner(new SpinnerNumberModel(ai.temperature(), 0.0, 2.0, 0.1));
        JSpinner maxTokensSpinner = new JSpinner(new SpinnerNumberModel(ai.maxTokens(), 16, 8192, 16));
        JCheckBox sendSensitiveDefaultBox = new JCheckBox("Send sensitive headers/values to AI by default", ai.isSendSensitiveByDefault());
        sendSensitiveDefaultBox.setToolTipText("Only pre-fills the AI Assistant tab's \"Include sensitive headers/values\" checkbox - sending is always a separate, explicit opt-in per request");

        aiPanel.add(row("Enabled:", enabledBox));
        aiPanel.add(row("Endpoint URL:", endpointField));
        aiPanel.add(row("API Key:", apiKeyField));
        aiPanel.add(row("Model:", modelField));
        aiPanel.add(row("Timeout (seconds):", timeoutSpinner));
        aiPanel.add(row("Temperature:", temperatureSpinner));
        aiPanel.add(row("Max Tokens:", maxTokensSpinner));
        aiPanel.add(row("", sendSensitiveDefaultBox));

        JLabel testResultLabel = new JLabel(" ");
        testResultLabel.setForeground(new Color(90, 90, 90));

        JPanel aiButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton save = new JButton("Save Settings");
        JButton test = new JButton("Test Connection");
        aiButtons.add(save);
        aiButtons.add(test);
        aiPanel.add(aiButtons);
        aiPanel.add(testResultLabel);

        save.addActionListener(e -> {
            AiSettings updated = new AiSettings();
            updated.setEnabled(enabledBox.isSelected());
            updated.setApiKey(apiKeyField.getText());
            updated.setEndpoint(endpointField.getText());
            updated.setModel(modelField.getText());
            updated.setTimeoutSeconds(((Number) timeoutSpinner.getValue()).intValue());
            updated.setTemperature(((Number) temperatureSpinner.getValue()).doubleValue());
            updated.setMaxTokens(((Number) maxTokensSpinner.getValue()).intValue());
            updated.setSendSensitiveByDefault(sendSensitiveDefaultBox.isSelected());
            state.setAiSettings(updated);
            state.persistenceManager().saveScopeFilter(state.scopeFilter());
            JOptionPane.showMessageDialog(this, "Settings saved.", "Saved", JOptionPane.INFORMATION_MESSAGE);
        });

        test.addActionListener(e -> {
            AiSettings candidate = new AiSettings();
            candidate.setEnabled(true);
            candidate.setApiKey(apiKeyField.getText());
            candidate.setEndpoint(endpointField.getText());
            candidate.setModel(modelField.getText());
            candidate.setTimeoutSeconds(((Number) timeoutSpinner.getValue()).intValue());
            candidate.setTemperature(0.0);
            candidate.setMaxTokens(16);
            test.setEnabled(false);
            testResultLabel.setText("Testing connection...");
            new Thread(() -> {
                try {
                    String reply = new DeepSeekClient().testConnection(candidate);
                    SwingUtilities.invokeLater(() -> {
                        testResultLabel.setForeground(new Color(30, 130, 30));
                        testResultLabel.setText("Connection OK - reply: " + truncate(reply));
                        test.setEnabled(true);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        testResultLabel.setForeground(new Color(170, 30, 30));
                        testResultLabel.setText("Connection failed: " + ex.getMessage());
                        test.setEnabled(true);
                    });
                }
            }, "payload-extractor-ai-test").start();
        });

        add(aiPanel, BorderLayout.NORTH);

        // ---- General
        JPanel generalPanel = new JPanel();
        generalPanel.setLayout(new BoxLayout(generalPanel, BoxLayout.Y_AXIS));
        generalPanel.setBorder(BorderFactory.createTitledBorder("General"));

        JCheckBox passiveLearningBox = new JCheckBox(
                "Passively learn payloads from ALL Burp traffic (Proxy, Repeater, Scanner, ...), not just messages sent to this extension",
                state.scopeFilter().isPassiveLearningEnabled());
        passiveLearningBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        passiveLearningBox.addActionListener(e -> {
            state.scopeFilter().setPassiveLearningEnabled(passiveLearningBox.isSelected());
            state.persistenceManager().saveScopeFilter(state.scopeFilter());
        });
        generalPanel.add(passiveLearningBox);

        JLabel persistenceNote = new JLabel("<html>Remembered payloads and settings are stored in Burp's project file via the extension "
                + "persistence API. In Burp Community Edition, projects are in-memory only, so this data does not "
                + "survive a Burp restart unless you're on Professional with a saved project file.</html>");
        persistenceNote.setFont(persistenceNote.getFont().deriveFont(Font.ITALIC, persistenceNote.getFont().getSize2D() - 1f));
        persistenceNote.setForeground(new Color(120, 120, 120));
        persistenceNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        persistenceNote.setBorder(BorderFactory.createEmptyBorder(4, 20, 4, 0));
        generalPanel.add(persistenceNote);

        JButton advancedScopeButton = new JButton("Advanced: host include/exclude patterns...");
        advancedScopeButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        advancedScopeButton.addActionListener(e -> {
            ScopeFilterBar bar = new ScopeFilterBar(state);
            bar.setPreferredSize(new Dimension(560, 320));
            JOptionPane.showMessageDialog(this, bar, "Host Include/Exclude Patterns", JOptionPane.PLAIN_MESSAGE);
        });
        generalPanel.add(Box.createVerticalStrut(6));
        generalPanel.add(advancedScopeButton);

        add(generalPanel, BorderLayout.CENTER);
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

    private static JPanel row(String label, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(520, 28));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(160, 20));
        panel.add(l, BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }
}
