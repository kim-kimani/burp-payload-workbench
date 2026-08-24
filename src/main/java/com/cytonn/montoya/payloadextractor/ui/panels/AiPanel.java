package com.cytonn.montoya.payloadextractor.ui.panels;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.ai.AiSuggestionParser;
import com.cytonn.montoya.payloadextractor.ai.DeepSeekClient;
import com.cytonn.montoya.payloadextractor.db.PayloadCollection;
import com.cytonn.montoya.payloadextractor.db.PayloadSource;
import com.cytonn.montoya.payloadextractor.db.PayloadValue;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "AI Assistant": send the current Workbench request/response (optionally redacted) plus a free-form
 * prompt to DeepSeek, review its reply, and pull any suggested values straight into a Payload
 * Collection. Sending is always opt-in per click - the "Include sensitive headers/values" checkbox
 * only pre-fills from {@code AiSettings.sendSensitiveByDefault()}, it never sends anything silently.
 */
public final class AiPanel extends JPanel {

    private static final Set<String> SENSITIVE_HEADER_NAMES = Set.of(
            "authorization", "cookie", "set-cookie", "x-api-key", "x-auth-token", "proxy-authorization");
    private static final Pattern AUTH_BEARER = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9\\-._~+/]+=*");

    private final ExtensionState state;

    private final JCheckBox includeFullRequest = new JCheckBox("Full request", true);
    private final JCheckBox includeFullResponse = new JCheckBox("Full response", true);
    private final JCheckBox includeSensitive = new JCheckBox("Include sensitive headers/values");
    private final JComboBox<String> focusParamCombo = new JComboBox<>();
    private final JTextArea promptArea = new JTextArea(
            "Suggest 5 realistic, varied candidate values for the focus parameter above, appropriate "
                    + "for authorized security testing (edge cases, boundary values, common defaults, or "
                    + "realistic-looking data as appropriate). Reply with a short explanation, then a JSON "
                    + "array of the values.");
    private final JButton sendButton = new JButton("Send to DeepSeek");
    private final JTextArea responseArea = new JTextArea();
    private final DefaultListModel<String> suggestionsModel = new DefaultListModel<>();
    private final JList<String> suggestionsList = new JList<>(suggestionsModel);
    private final JComboBox<String> addToCombo = new JComboBox<>();
    private final JLabel statusLabel = new JLabel();

    public AiPanel(ExtensionState state) {
        super(new BorderLayout(8, 8));
        this.state = state;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        includeSensitive.setSelected(state.aiSettings().isSendSensitiveByDefault());

        add(buildTop(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBottom(), BorderLayout.SOUTH);

        addAncestorListenerForRefresh();
        refreshStatus();
    }

    private JPanel buildTop() {
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        statusLabel.setForeground(new Color(90, 90, 90));
        top.add(statusLabel);

        JPanel checkboxRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        checkboxRow.add(includeFullRequest);
        checkboxRow.add(includeFullResponse);
        checkboxRow.add(includeSensitive);
        top.add(checkboxRow);

        JPanel focusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        focusRow.add(new JLabel("Focus parameter:"));
        focusParamCombo.addItem("(none)");
        focusParamCombo.setPreferredSize(new Dimension(220, 24));
        focusRow.add(focusParamCombo);
        JButton refreshFieldsButton = new JButton("Refresh from Workbench");
        refreshFieldsButton.addActionListener(e -> refreshFromWorkbench());
        focusRow.add(refreshFieldsButton);
        top.add(focusRow);

        return top;
    }

    private JSplitPane buildCenter() {
        JPanel promptPanel = new JPanel(new BorderLayout(4, 4));
        promptPanel.setBorder(BorderFactory.createTitledBorder("Prompt"));
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptPanel.add(new JScrollPane(promptArea), BorderLayout.CENTER);
        sendButton.addActionListener(e -> send());
        JPanel sendRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        sendRow.add(sendButton);
        promptPanel.add(sendRow, BorderLayout.SOUTH);
        promptPanel.setPreferredSize(new Dimension(0, 160));

        responseArea.setEditable(false);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(true);
        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBorder(BorderFactory.createTitledBorder("DeepSeek Response"));
        responsePanel.add(new JScrollPane(responseArea), BorderLayout.CENTER);

        suggestionsList.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JPanel suggestionsPanel = new JPanel(new BorderLayout());
        suggestionsPanel.setBorder(BorderFactory.createTitledBorder("Suggested Values"));
        suggestionsPanel.add(new JScrollPane(suggestionsList), BorderLayout.CENTER);

        JSplitPane resultsSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, responsePanel, suggestionsPanel);
        resultsSplit.setResizeWeight(0.55);

        JSplitPane outer = new JSplitPane(JSplitPane.VERTICAL_SPLIT, promptPanel, resultsSplit);
        outer.setResizeWeight(0.3);
        return outer;
    }

    private JPanel buildBottom() {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bottom.add(new JLabel("Add to:"));
        addToCombo.setPreferredSize(new Dimension(200, 24));
        bottom.add(addToCombo);
        JButton addSelected = new JButton("Add Selected");
        addSelected.addActionListener(e -> addValues(suggestionsList.getSelectedValuesList()));
        JButton addAll = new JButton("Add All");
        addAll.addActionListener(e -> {
            java.util.List<String> all = new java.util.ArrayList<>();
            for (int i = 0; i < suggestionsModel.size(); i++) {
                all.add(suggestionsModel.get(i));
            }
            addValues(all);
        });
        bottom.add(addSelected);
        bottom.add(addAll);
        return bottom;
    }

    private void addAncestorListenerForRefresh() {
        addHierarchyListener(e -> {
            if (isShowing()) {
                refreshFromWorkbench();
                refreshStatus();
            }
        });
    }

    private void refreshStatus() {
        statusLabel.setText(state.aiSettings().isConfigured()
                ? "DeepSeek: configured (model " + state.aiSettings().model() + ")"
                : "DeepSeek: not configured - set an API key in the Settings tab");
    }

    private void refreshFromWorkbench() {
        String previousFocus = (String) focusParamCombo.getSelectedItem();
        focusParamCombo.removeAllItems();
        focusParamCombo.addItem("(none)");
        List<ParsedField> fields = state.mainPanel() != null ? state.mainPanel().workbenchPanel().currentWorkingFields() : List.of();
        for (ParsedField f : fields) {
            focusParamCombo.addItem(f.name());
        }
        if (previousFocus != null) {
            focusParamCombo.setSelectedItem(previousFocus);
        }

        Object previousTarget = addToCombo.getSelectedItem();
        addToCombo.removeAllItems();
        for (PayloadCollection c : state.database().allCollections()) {
            addToCombo.addItem(c.normalizedName());
        }
        if (previousTarget != null) {
            addToCombo.setSelectedItem(previousTarget);
        }
    }

    private void send() {
        if (!state.aiSettings().isConfigured()) {
            JOptionPane.showMessageDialog(this, "Configure and enable an API key in the Settings tab first.", "Not configured", JOptionPane.WARNING_MESSAGE);
            return;
        }
        HttpRequestResponse rr = state.mainPanel() != null ? state.mainPanel().workbenchPanel().currentRequestResponse() : null;
        if (rr == null && (includeFullRequest.isSelected() || includeFullResponse.isSelected())) {
            int proceed = JOptionPane.showConfirmDialog(this,
                    "No request is loaded in the Workbench, so \"Full request\"/\"Full response\" will be skipped. Continue with just the prompt?",
                    "No request loaded", JOptionPane.YES_NO_OPTION);
            if (proceed != JOptionPane.YES_OPTION) {
                return;
            }
        }

        String focus = (String) focusParamCombo.getSelectedItem();
        boolean hasFocus = focus != null && !"(none)".equals(focus);

        StringBuilder system = new StringBuilder();
        system.append("You are a security testing assistant helping a penetration tester analyze an HTTP ")
              .append("request/response for authorized security testing. Reply with a short explanation, ")
              .append("then - if you have concrete value suggestions - a JSON array of short string values, ")
              .append("e.g. [\"value1\",\"value2\"], on its own line.\n\n");
        if (hasFocus) {
            system.append("Focus parameter: ").append(focus).append("\n\n");
        }
        if (rr != null && includeFullRequest.isSelected()) {
            system.append("--- Full request ---\n").append(redactIfNeeded(rr.request().toString())).append("\n\n");
        }
        if (rr != null && rr.hasResponse() && includeFullResponse.isSelected()) {
            system.append("--- Full response ---\n").append(redactIfNeeded(rr.response().toString())).append("\n\n");
        }

        String userPrompt = promptArea.getText();

        sendButton.setEnabled(false);
        responseArea.setText("Sending...");
        suggestionsModel.clear();
        new Thread(() -> {
            try {
                String reply = new DeepSeekClient().chat(state.aiSettings(), system.toString(), userPrompt);
                List<String> suggestions = AiSuggestionParser.parseContentAsSuggestions(extractJsonArrayOrWholeText(reply));
                SwingUtilities.invokeLater(() -> {
                    responseArea.setText(reply);
                    responseArea.setCaretPosition(0);
                    suggestionsModel.clear();
                    for (String s : suggestions) {
                        suggestionsModel.addElement(s);
                    }
                    sendButton.setEnabled(true);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    responseArea.setText("Request failed: " + ex.getMessage());
                    sendButton.setEnabled(true);
                });
            }
        }, "payload-extractor-ai-assistant").start();
    }

    /** If the reply contains prose plus a JSON array, isolate the array; otherwise fall back to line-based parsing of the whole reply. */
    private static String extractJsonArrayOrWholeText(String reply) {
        if (reply == null) {
            return "";
        }
        int start = reply.indexOf('[');
        int end = reply.lastIndexOf(']');
        return (start >= 0 && end > start) ? reply.substring(start, end + 1) : reply;
    }

    private String redactIfNeeded(String rawMessageText) {
        if (includeSensitive.isSelected() || rawMessageText == null) {
            return rawMessageText;
        }
        StringBuilder out = new StringBuilder();
        for (String line : rawMessageText.split("\r?\n", -1)) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                String headerName = line.substring(0, colon).trim().toLowerCase();
                if (SENSITIVE_HEADER_NAMES.contains(headerName)) {
                    out.append(line, 0, colon + 1).append(" [REDACTED]\n");
                    continue;
                }
            }
            out.append(AUTH_BEARER.matcher(line).replaceAll("$1[REDACTED]")).append('\n');
        }
        return out.toString();
    }

    private void addValues(List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        String targetName = (String) addToCombo.getSelectedItem();
        if (targetName == null || targetName.isBlank()) {
            JOptionPane.showMessageDialog(this, "Pick a collection to add to (or create one in Payload Collections first).", "No target", JOptionPane.WARNING_MESSAGE);
            return;
        }
        PayloadCollection target = state.database().findOrCreate(targetName, "GENERIC");
        long now = System.currentTimeMillis();
        for (String v : values) {
            target.add(new PayloadValue(null, v, PayloadSource.AI_SUGGESTED, now, null, null));
        }
        state.persistenceManager().saveDatabase(state.database());
        JOptionPane.showMessageDialog(this, values.size() + " value(s) added to \"" + targetName + "\".", "Added", JOptionPane.INFORMATION_MESSAGE);
    }
}
