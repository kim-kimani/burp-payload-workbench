package com.cytonn.montoya.payloadextractor.ui.panels;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.db.PayloadCollection;
import com.cytonn.montoya.payloadextractor.db.PayloadSource;
import com.cytonn.montoya.payloadextractor.db.PayloadValue;
import com.cytonn.montoya.payloadextractor.detector.PayloadDetector;
import com.cytonn.montoya.payloadextractor.history.HistoryEntry;
import com.cytonn.montoya.payloadextractor.modifier.RequestModifier;
import com.cytonn.montoya.payloadextractor.parser.MessageDirection;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;
import com.cytonn.montoya.payloadextractor.ui.AddFieldDialog;
import com.cytonn.montoya.payloadextractor.ui.GeneratorDialog;
import com.cytonn.montoya.payloadextractor.ui.ReplayConfigDialog;
import com.cytonn.montoya.payloadextractor.util.JsonNode;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The "Observe -> Extract -> Remember -> Categorize -> Modify -> Generate -> Replay -> Analyze"
 * workstation. This is where v1.3.0's real-composition fix lives: every Add / Duplicate /
 * drag-reorder / "X" remove action here goes through {@link WorkbenchHooks}, which always ends in a
 * call to {@link RequestModifier#buildComposedRequest} - so the Modified Request editor (and, when
 * you hit Send or Replay, the actual outgoing traffic) reflects the real JSON body / cookie / header
 * / parameter state, not just the on-screen rows.
 *
 * <p>Layout (v1.4.0): a horizontal top area - Original Request on the left, Modified Request plus
 * its Response on the right - with a full-width, compact, single-line-per-field "Detected Payloads"
 * list underneath, so every field's action buttons are always visible without horizontal scrolling.
 */
public final class WorkbenchPanel extends JPanel implements WorkbenchHooks {

    private final ExtensionState state;

    private HttpRequestResponse originalRequestResponse;
    private List<ParsedField> baselineFields = new ArrayList<>();
    private List<ParsedField> workingFields = new ArrayList<>();

    private final HttpRequestEditor originalRequestEditor;
    private final HttpRequestEditor modifiedRequestEditor;
    private final HttpResponseEditor originalResponseEditor;
    private final HttpResponseEditor modifiedResponseEditor;
    private final JTabbedPane responseTabs = new JTabbedPane();

    private final JPanel requestFieldsContainer = new JPanel();
    private final JPanel responseFieldsContainer = new JPanel();
    private final JLabel statusLabel = new JLabel("No request loaded. Right-click a request anywhere in Burp and choose \"Send to Payload Extractor\".");
    private final JButton sendButton = new JButton("Send Modified Request");

    public WorkbenchPanel(ExtensionState state) {
        super(new BorderLayout(8, 8));
        this.state = state;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        originalRequestEditor = state.api().userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        modifiedRequestEditor = state.api().userInterface().createHttpRequestEditor();
        originalResponseEditor = state.api().userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        modifiedResponseEditor = state.api().userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);

        // ---- toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JButton addFieldButton = new JButton("+ Add Field");
        addFieldButton.addActionListener(e -> onAddFieldClicked());
        JButton rememberAllButton = new JButton("Remember All Values");
        rememberAllButton.setToolTipText("Save every current field value into the payload collection database");
        rememberAllButton.addActionListener(e -> rememberAll());
        JButton rescanButton = new JButton("Re-scan Modified Request");
        rescanButton.setToolTipText("Re-detect fields from the Modified Request editor's current text (picks up any raw edits typed directly into it)");
        rescanButton.addActionListener(e -> rescanModifiedRequest());
        JButton resetButton = new JButton("Reset to Original");
        resetButton.setToolTipText("Discard every Add/Duplicate/edit/reorder/remove and go back to the pristine original request");
        resetButton.addActionListener(e -> resetToOriginal());
        sendButton.setToolTipText("Actually send the composed Modified Request and show its response");
        sendButton.addActionListener(e -> sendModifiedRequest());

        toolbar.add(addFieldButton);
        toolbar.add(rememberAllButton);
        toolbar.add(rescanButton);
        toolbar.add(resetButton);
        toolbar.add(sendButton);
        add(toolbar, BorderLayout.NORTH);
        add(statusLabel, BorderLayout.SOUTH);

        // ---- top area: Original Request (left) | Modified Request + Response (right)
        JPanel originalPanel = new JPanel(new BorderLayout());
        originalPanel.setBorder(BorderFactory.createTitledBorder("Original Request"));
        originalPanel.add(originalRequestEditor.uiComponent(), BorderLayout.CENTER);

        JPanel modifiedRequestPanel = new JPanel(new BorderLayout());
        modifiedRequestPanel.setBorder(BorderFactory.createTitledBorder("Modified Request"));
        modifiedRequestPanel.add(modifiedRequestEditor.uiComponent(), BorderLayout.CENTER);

        responseTabs.addTab("Original Response", originalResponseEditor.uiComponent());
        responseTabs.addTab("Response (after Send)", modifiedResponseEditor.uiComponent());
        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBorder(BorderFactory.createTitledBorder("Response"));
        responsePanel.add(responseTabs, BorderLayout.CENTER);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, modifiedRequestPanel, responsePanel);
        rightSplit.setResizeWeight(0.6);

        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, originalPanel, rightSplit);
        topSplit.setResizeWeight(0.4);

        // ---- bottom area: full-width Detected Payloads
        requestFieldsContainer.setLayout(new BoxLayout(requestFieldsContainer, BoxLayout.Y_AXIS));
        responseFieldsContainer.setLayout(new BoxLayout(responseFieldsContainer, BoxLayout.Y_AXIS));

        JPanel detectedPanel = new JPanel(new BorderLayout());
        detectedPanel.setBorder(BorderFactory.createTitledBorder("Detected Payloads"));
        JTabbedPane detectedTabs = new JTabbedPane();
        detectedTabs.addTab("Request Fields", new JScrollPane(requestFieldsContainer));
        detectedTabs.addTab("Response Fields", new JScrollPane(responseFieldsContainer));
        detectedPanel.add(detectedTabs, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, detectedPanel);
        mainSplit.setResizeWeight(0.55);
        add(mainSplit, BorderLayout.CENTER);
    }

    // ---------------------------------------------------------------- loading

    public void openInWorkbench(HttpRequestResponse rr) {
        this.originalRequestResponse = rr;
        this.baselineFields = new ArrayList<>();
        baselineFields.addAll(PayloadDetector.detectRequest(rr.request()));
        if (rr.hasResponse()) {
            baselineFields.addAll(PayloadDetector.detectResponse(rr.response()));
        }
        this.workingFields = new ArrayList<>();
        for (ParsedField f : baselineFields) {
            if (f.direction() == MessageDirection.REQUEST) {
                workingFields.add(f.copyForWorking());
            }
        }

        originalRequestEditor.setRequest(rr.request());
        if (rr.hasResponse()) {
            originalResponseEditor.setResponse(rr.response());
        }
        responseTabs.setSelectedIndex(0);

        rebuildRequestFieldsUi();
        rebuildResponseFieldsUi(rr);
        recompose();
        int autoRemembered = autoRememberOnLoad(rr);
        statusLabel.setText("Loaded " + rr.request().method() + " " + rr.request().path()
                + "  -  " + workingFields.size() + " request field(s) detected"
                + (autoRemembered > 0 ? "  -  " + autoRemembered + " value(s) auto-remembered" : ""));
    }

    /**
     * Auto-remembers every non-blank detected value (request + response) as soon as a request loads,
     * so the analyst never has to click "Remember" by hand for the common case - gated by the
     * "Passively learn payloads" master switch and the scope include/exclude patterns in Settings.
     * {@link PayloadCollection#add} is dedupe-aware, so loading the same request repeatedly never
     * piles up duplicate entries.
     */
    private int autoRememberOnLoad(HttpRequestResponse rr) {
        if (!state.scopeFilter().isPassiveLearningEnabled()) {
            return 0;
        }
        String host = rr.httpService() != null ? rr.httpService().host() : null;
        if (!state.scopeFilter().isInScope(host)) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int count = 0;
        for (ParsedField f : baselineFields) {
            if (!f.currentValue().isBlank()) {
                state.database().remember(f.path(), f.category(), f.currentValue(), PayloadSource.OBSERVED, now, host);
                count++;
            }
        }
        if (count > 0) {
            state.persistenceManager().saveDatabase(state.database());
        }
        return count;
    }

    // ---------------------------------------------------------------- composition

    private void recompose() {
        if (originalRequestResponse == null) {
            return;
        }
        HttpRequest composed = RequestModifier.buildComposedRequest(originalRequestResponse.request(), baselineFields, workingFields);
        modifiedRequestEditor.setRequest(composed);
    }

    /** The fully-composed request with every field EXCEPT the given one settled - the base a replay run varies one field on top of. */
    public HttpRequest composedRequestForReplay() {
        return RequestModifier.buildComposedRequest(originalRequestResponse.request(), baselineFields, workingFields);
    }

    /** The request/response currently loaded in the Workbench, or {@code null} if nothing is loaded yet - used by the AI Assistant tab to build request/response context. */
    public HttpRequestResponse currentRequestResponse() {
        return originalRequestResponse;
    }

    /** A snapshot of the current working field list - used by the AI Assistant tab's Focus Parameter dropdown. */
    public List<ParsedField> currentWorkingFields() {
        return new ArrayList<>(workingFields);
    }

    /**
     * Loads a request/response (typically from the Intercept tab's "Send to Replay") and, if any
     * detected field looks interesting (non-generic category), immediately opens the Replay
     * configuration dialog for it - otherwise just lands in the Workbench like a normal open.
     */
    public void openInWorkbenchAndReplay(HttpRequestResponse rr) {
        openInWorkbench(rr);
        Optional<ParsedField> target = workingFields.stream().filter(f -> !"GENERIC".equals(f.category())).findFirst();
        if (target.isEmpty() && !workingFields.isEmpty()) {
            target = Optional.of(workingFields.get(0));
        }
        target.ifPresent(this::onReplayRequested);
    }

    // ---------------------------------------------------------------- grouping / rendering

    private String groupKeyOf(ParsedField f) {
        switch (f.location()) {
            case JSON_BODY: return "JSON:" + JsonNode.parentPathOf(f.path());
            case COOKIE: return "COOKIE";
            case HEADER: return "HEADER";
            case URL_PARAM: return "URL_PARAM";
            case FORM_PARAM: return "FORM_PARAM";
            case MULTIPART_PARAM: return "MULTIPART_PARAM";
            case XML_BODY: return "XML_BODY";
            default: return "RAW_BODY";
        }
    }

    private String groupTitle(String key, ParsedField sample) {
        if (key.startsWith("JSON:")) {
            String parent = key.substring("JSON:".length());
            return "JSON Body" + (parent.isEmpty() ? " (root)" : " → " + parent);
        }
        return sample.location().displayName();
    }

    private void rebuildRequestFieldsUi() {
        requestFieldsContainer.removeAll();

        if (workingFields.isEmpty()) {
            requestFieldsContainer.add(new JLabel("No fields detected. Use + Add Field to add one by hand."));
        }

        Map<String, List<ParsedField>> groups = new LinkedHashMap<>();
        for (ParsedField f : workingFields) {
            groups.computeIfAbsent(groupKeyOf(f), k -> new ArrayList<>()).add(f);
        }

        for (Map.Entry<String, List<ParsedField>> entry : groups.entrySet()) {
            List<ParsedField> groupFields = entry.getValue();
            if (groupFields.isEmpty()) {
                continue;
            }
            JPanel section = new JPanel();
            section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
            section.setBorder(BorderFactory.createTitledBorder(groupTitle(entry.getKey(), groupFields.get(0))));
            section.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel list = new JPanel();
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            for (ParsedField f : groupFields) {
                PayloadFieldComponent box = new PayloadFieldComponent(f, this);
                box.setAlignmentX(Component.LEFT_ALIGNMENT);
                list.add(box);
            }
            section.add(list);
            requestFieldsContainer.add(section);
            requestFieldsContainer.add(Box.createVerticalStrut(4));
        }

        requestFieldsContainer.revalidate();
        requestFieldsContainer.repaint();
    }

    private void rebuildResponseFieldsUi(HttpRequestResponse rr) {
        responseFieldsContainer.removeAll();
        if (!rr.hasResponse()) {
            responseFieldsContainer.add(new JLabel("No response captured for this request."));
            responseFieldsContainer.revalidate();
            responseFieldsContainer.repaint();
            return;
        }
        List<ParsedField> responseFields = baselineFields.stream()
                .filter(f -> f.direction() == MessageDirection.RESPONSE)
                .toList();
        if (responseFields.isEmpty()) {
            responseFieldsContainer.add(new JLabel("No fields detected in the response."));
        }
        for (ParsedField f : responseFields) {
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
            JLabel label = new JLabel(f.name());
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setPreferredSize(new Dimension(220, 20));
            row.add(label, BorderLayout.WEST);
            JTextField valueField = new JTextField(f.currentValue());
            valueField.setEditable(false);
            row.add(valueField, BorderLayout.CENTER);
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            boolean alreadyRemembered = isFavoriteEligible(f);
            JButton remember = new JButton(alreadyRemembered ? "Remembered" : "Remember");
            remember.addActionListener(e -> {
                String host = rr.httpService() != null ? rr.httpService().host() : null;
                state.database().remember(f.path(), f.category(), f.currentValue(), PayloadSource.OBSERVED, System.currentTimeMillis(), host);
                state.persistenceManager().saveDatabase(state.database());
                remember.setText("Remembered");
            });
            actions.add(remember);
            row.add(actions, BorderLayout.EAST);
            responseFieldsContainer.add(row);
        }
        responseFieldsContainer.revalidate();
        responseFieldsContainer.repaint();
    }

    private boolean isFavoriteEligible(ParsedField f) {
        return state.database().find(com.cytonn.montoya.payloadextractor.detector.NameNormalizer.normalForm(f.path()))
                .map(c -> c.values().stream().anyMatch(v -> v.value().equals(f.currentValue())))
                .orElse(false);
    }

    // ---------------------------------------------------------------- toolbar actions

    private void onAddFieldClicked() {
        if (originalRequestResponse == null) {
            JOptionPane.showMessageDialog(this, "Load a request first.", "No request", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Optional<ParsedField> added = AddFieldDialog.showDialog(this, originalRequestResponse.request(), baselineFields, workingFields, null);
        added.ifPresent(field -> {
            workingFields.add(field);
            state.historyManager().record(HistoryEntry.of(HistoryEntry.Action.FIELD_ADDED, field.name(), field.location(),
                    "", field.currentValue(), "manual", System.currentTimeMillis()));
            rebuildRequestFieldsUi();
            recompose();
        });
    }

    private void rememberAll() {
        if (originalRequestResponse == null) {
            return;
        }
        String host = originalRequestResponse.httpService() != null ? originalRequestResponse.httpService().host() : null;
        long now = System.currentTimeMillis();
        for (ParsedField f : workingFields) {
            if (!f.currentValue().isBlank()) {
                state.database().remember(f.path(), f.category(), f.currentValue(), PayloadSource.MANUAL, now, host);
            }
        }
        state.persistenceManager().saveDatabase(state.database());
        JOptionPane.showMessageDialog(this, "Remembered " + workingFields.size() + " value(s).", "Saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private void rescanModifiedRequest() {
        if (originalRequestResponse == null) {
            JOptionPane.showMessageDialog(this, "Load a request first.", "No request", JOptionPane.WARNING_MESSAGE);
            return;
        }
        HttpRequest current = modifiedRequestEditor.getRequest();
        if (current == null) {
            return;
        }
        List<ParsedField> rescanned = PayloadDetector.detectRequest(current);
        workingFields = new ArrayList<>();
        for (ParsedField f : rescanned) {
            workingFields.add(f.copyForWorking());
        }
        rebuildRequestFieldsUi();
        recompose();
        statusLabel.setText("Re-scanned Modified Request - " + workingFields.size() + " field(s) detected");
    }

    private void resetToOriginal() {
        if (originalRequestResponse == null) {
            return;
        }
        workingFields = new ArrayList<>();
        for (ParsedField f : baselineFields) {
            if (f.direction() == MessageDirection.REQUEST) {
                workingFields.add(f.copyForWorking());
            }
        }
        rebuildRequestFieldsUi();
        recompose();
        statusLabel.setText("Reset to original - " + workingFields.size() + " field(s)");
    }

    private void sendModifiedRequest() {
        if (originalRequestResponse == null) {
            JOptionPane.showMessageDialog(this, "Load a request first.", "No request", JOptionPane.WARNING_MESSAGE);
            return;
        }
        HttpRequest composed = composedRequestForReplay();
        sendButton.setEnabled(false);
        statusLabel.setText("Sending modified request...");
        new Thread(() -> {
            try {
                HttpRequestResponse result = state.api().http().sendRequest(composed);
                SwingUtilities.invokeLater(() -> onSendCompleted(result));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Send failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                    statusLabel.setText("Send failed: " + ex.getMessage());
                    sendButton.setEnabled(true);
                });
            }
        }, "payload-extractor-send").start();
    }

    private void onSendCompleted(HttpRequestResponse result) {
        String host = result.httpService() != null ? result.httpService().host() : null;
        Integer status = null;
        if (result.hasResponse()) {
            modifiedResponseEditor.setResponse(result.response());
            responseTabs.setSelectedIndex(1);
            status = (int) result.response().statusCode();
        }
        Long sizeBytes = result.hasResponse() ? (long) result.response().toByteArray().length() : null;
        for (ParsedField f : workingFields) {
            if (f.isDirty() || f.manuallyAdded()) {
                state.historyManager().record(HistoryEntry.of(HistoryEntry.Action.VALUE_CHANGED, f.name(), f.location(),
                        f.originalValue(), f.currentValue(), "sent", System.currentTimeMillis(), host, status)
                        .withResponseSizeBytes(sizeBytes));
            }
        }
        // request+response awareness: learn values seen in the response too, same scope gating as auto-remember-on-load.
        if (result.hasResponse() && state.scopeFilter().isPassiveLearningEnabled() && state.scopeFilter().isInScope(host)) {
            long now = System.currentTimeMillis();
            for (ParsedField f : PayloadDetector.onlyInteresting(PayloadDetector.detectResponse(result.response()))) {
                if (!f.currentValue().isBlank()) {
                    state.database().remember(f.path(), f.category(), f.currentValue(), PayloadSource.OBSERVED, now, host);
                }
            }
            state.persistenceManager().saveDatabase(state.database());
        }
        statusLabel.setText("Sent. " + (result.hasResponse() ? "Response: HTTP " + result.response().statusCode() : "No response received."));
        sendButton.setEnabled(true);
    }

    // ---------------------------------------------------------------- WorkbenchHooks

    @Override
    public void onValueChanged(ParsedField field, String newValue) {
        recompose();
    }

    @Override
    public void onRemoveRequested(ParsedField field) {
        String oldValue = field.currentValue();
        workingFields.removeIf(f -> f.id().equals(field.id()));
        state.historyManager().record(HistoryEntry.of(HistoryEntry.Action.FIELD_REMOVED, field.name(), field.location(),
                oldValue, "", "manual", System.currentTimeMillis()));
        rebuildRequestFieldsUi();
        recompose();
    }

    @Override
    public void onDuplicateRequested(ParsedField field) {
        Optional<ParsedField> added = AddFieldDialog.showDialog(this, originalRequestResponse.request(), baselineFields, workingFields, field);
        added.ifPresent(newField -> {
            int idx = indexOfById(workingFields, field.id());
            workingFields.add(idx < 0 ? workingFields.size() : idx + 1, newField);
            state.historyManager().record(HistoryEntry.of(HistoryEntry.Action.FIELD_DUPLICATED, newField.name(), newField.location(),
                    "", newField.currentValue(), "manual", System.currentTimeMillis()));
            rebuildRequestFieldsUi();
            recompose();
        });
    }

    @Override
    public void onReorderRequested(ParsedField field, int newIndexWithinGroup) {
        workingFields = FieldReorderUtil.reorderWithinGroup(workingFields, this::groupKeyOf, field, newIndexWithinGroup);
        state.historyManager().record(HistoryEntry.of(HistoryEntry.Action.FIELD_REORDERED, field.name(), field.location(),
                "", "moved to position " + newIndexWithinGroup, "manual", System.currentTimeMillis()));
        rebuildRequestFieldsUi();
        recompose();
    }

    @Override
    public void onMoveUpRequested(ParsedField field) {
        workingFields = FieldReorderUtil.moveUpWithinGroup(workingFields, this::groupKeyOf, field);
        state.historyManager().record(HistoryEntry.of(HistoryEntry.Action.FIELD_REORDERED, field.name(), field.location(),
                "", "moved up", "manual", System.currentTimeMillis()));
        rebuildRequestFieldsUi();
        recompose();
    }

    @Override
    public void onMoveDownRequested(ParsedField field) {
        workingFields = FieldReorderUtil.moveDownWithinGroup(workingFields, this::groupKeyOf, field);
        state.historyManager().record(HistoryEntry.of(HistoryEntry.Action.FIELD_REORDERED, field.name(), field.location(),
                "", "moved down", "manual", System.currentTimeMillis()));
        rebuildRequestFieldsUi();
        recompose();
    }

    @Override
    public void onPlayToggled(ParsedField field, boolean enabled) {
        // No composition impact by itself - governs which fields bulk operations act on.
    }

    @Override
    public void onGenerateRequested(ParsedField field) {
        List<String> values = GeneratorDialog.showDialog(this, state, field);
        if (!values.isEmpty()) {
            String old = field.currentValue();
            field.setCurrentValue(values.get(0));
            state.historyManager().record(HistoryEntry.of(HistoryEntry.Action.VALUE_CHANGED, field.name(), field.location(),
                    old, field.currentValue(), "generated", System.currentTimeMillis()));
            rebuildRequestFieldsUi();
            recompose();
        }
    }

    @Override
    public void onReplayRequested(ParsedField field) {
        HttpRequest base = composedRequestForReplay();
        ReplayConfigDialog.showDialog(this, state, base, field);
    }

    @Override
    public void onFavoriteToggled(ParsedField field) {
        PayloadCollection collection = collectionFor(field);
        Optional<PayloadValue> match = collection.values().stream().filter(v -> v.value().equals(field.currentValue())).findFirst();
        if (match.isPresent()) {
            match.get().setFavorite(!match.get().isFavorite());
        } else if (!field.currentValue().isBlank()) {
            String host = originalRequestResponse != null && originalRequestResponse.httpService() != null
                    ? originalRequestResponse.httpService().host() : null;
            PayloadValue created = new PayloadValue(null, field.currentValue(), PayloadSource.MANUAL, System.currentTimeMillis(), host, null, true);
            collection.add(created);
        }
        state.persistenceManager().saveDatabase(state.database());
    }

    @Override
    public boolean isFavorite(ParsedField field) {
        return collectionFor(field).values().stream()
                .filter(v -> v.value().equals(field.currentValue()))
                .anyMatch(PayloadValue::isFavorite);
    }

    @Override
    public PayloadCollection collectionFor(ParsedField field) {
        return state.database().findOrCreate(field.path(), field.category());
    }

    @Override
    public void onCategoryReassigned(ParsedField field, String newCategory) {
        field.setCategory(newCategory);
        collectionFor(field).setCategory(newCategory);
        state.persistenceManager().saveDatabase(state.database());
        rebuildRequestFieldsUi();
    }

    private static int indexOfById(List<ParsedField> fields, String id) {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
