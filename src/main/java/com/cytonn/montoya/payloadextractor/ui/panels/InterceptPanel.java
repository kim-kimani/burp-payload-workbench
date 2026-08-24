package com.cytonn.montoya.payloadextractor.ui.panels;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.intercept.InterceptDecision;
import com.cytonn.montoya.payloadextractor.intercept.InterceptDirection;
import com.cytonn.montoya.payloadextractor.intercept.InterceptEngine;
import com.cytonn.montoya.payloadextractor.intercept.InterceptedMessage;
import com.cytonn.montoya.payloadextractor.ui.InterceptConditionDialog;
import com.cytonn.montoya.payloadextractor.ui.ModificationRuleDialog;
import com.cytonn.montoya.payloadextractor.variables.Variable;
import com.cytonn.montoya.payloadextractor.variables.VariableResolver;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * "Intercept": a genuine Montoya-native request/response hold point (see {@link InterceptEngine}),
 * a searchable/sortable REQUEST HISTORY of every message that passes through it, and 50/50
 * Request/Response editors reusing the same Montoya editor components the Workbench uses. Sits
 * immediately before the Workbench tab in {@link MainPanel}.
 */
public final class InterceptPanel extends JPanel implements InterceptEngine.Listener {

    private final ExtensionState state;
    private final InterceptEngine engine;

    private final HttpRequestEditor requestEditor;
    private final HttpResponseEditor responseEditor;

    private final InterceptTableModel tableModel = new InterceptTableModel();
    private final JTable table = new JTable(tableModel);
    private final TableRowSorter<InterceptTableModel> sorter;
    private final JTextField searchField = new JTextField(18);
    private final JComboBox<String> methodFilter = new JComboBox<>(new String[]{"Any method", "GET", "POST", "PUT", "PATCH", "DELETE"});
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[]{"Any status", "2xx", "3xx", "4xx", "5xx"});
    private final JCheckBox pinnedOnlyBox = new JCheckBox("Pinned only");
    private final JLabel countLabel = new JLabel(" ");

    private final JToggleButton masterToggle = new JToggleButton("INTERCEPT: OFF");
    private final JCheckBox interceptRequestsBox = new JCheckBox("Requests", true);
    private final JCheckBox interceptResponsesBox = new JCheckBox("Responses", false);
    private final JCheckBox automaticEditorBox = new JCheckBox("Automatic Editor");
    private final JButton forwardButton = new JButton("Forward");
    private final JButton forwardEditButton = new JButton("Forward & Edit");
    private final JButton dropButton = new JButton("Drop");

    private InterceptedMessage selected;

    public InterceptPanel(ExtensionState state) {
        super(new BorderLayout(6, 6));
        this.state = state;
        this.engine = state.interceptEngine();
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        requestEditor = state.api().userInterface().createHttpRequestEditor();
        responseEditor = state.api().userInterface().createHttpResponseEditor();

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        table.setAutoCreateRowSorter(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(45);
        table.getColumnModel().getColumn(1).setPreferredWidth(35);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(buildInterceptBar());
        top.add(buildActionBar());
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(buildFilterBar(), BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(0, 220));
        center.add(tableScroll, BorderLayout.CENTER);
        center.add(countLabel, BorderLayout.SOUTH);

        JPanel requestPanel = new JPanel(new BorderLayout());
        requestPanel.setBorder(BorderFactory.createTitledBorder("Request"));
        requestPanel.add(requestEditor.uiComponent(), BorderLayout.CENTER);
        JButton ruleFromReqSelection = new JButton("Create Rule from Selection");
        ruleFromReqSelection.addActionListener(e -> createRuleFromSelection(requestEditor.selection().map(s -> s.contents().toString()).orElse(null), InterceptDirection.REQUEST));
        JButton varFromReqSelection = new JButton("Extract Selected as Variable");
        varFromReqSelection.addActionListener(e -> extractSelectionAsVariable(requestEditor.selection().map(s -> s.contents().toString()).orElse(null)));
        JPanel reqSouth = new JPanel(new FlowLayout(FlowLayout.LEFT));
        reqSouth.add(ruleFromReqSelection);
        reqSouth.add(varFromReqSelection);
        requestPanel.add(reqSouth, BorderLayout.SOUTH);

        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.setBorder(BorderFactory.createTitledBorder("Response"));
        responsePanel.add(responseEditor.uiComponent(), BorderLayout.CENTER);
        JButton ruleFromRespSelection = new JButton("Create Rule from Selection");
        ruleFromRespSelection.addActionListener(e -> createRuleFromSelection(responseEditor.selection().map(s -> s.contents().toString()).orElse(null), InterceptDirection.RESPONSE));
        JButton varFromRespSelection = new JButton("Extract Selected as Variable");
        varFromRespSelection.addActionListener(e -> extractSelectionAsVariable(responseEditor.selection().map(s -> s.contents().toString()).orElse(null)));
        JPanel respSouth = new JPanel(new FlowLayout(FlowLayout.LEFT));
        respSouth.add(ruleFromRespSelection);
        respSouth.add(varFromRespSelection);
        JButton diffButton = new JButton("Original vs Modified...");
        diffButton.addActionListener(e -> showDiff());
        respSouth.add(diffButton);
        responsePanel.add(respSouth, BorderLayout.SOUTH);

        JSplitPane editorsSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestPanel, responsePanel);
        editorsSplit.setResizeWeight(0.5);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, editorsSplit);
        mainSplit.setResizeWeight(0.4);
        add(mainSplit, BorderLayout.CENTER);

        wireTableSelection();
        wireTableContextMenu();
        wireFilters();

        engine.setListener(this);
        for (InterceptedMessage m : engine.history()) {
            tableModel.addRow(m);
        }
        updateCountLabel();
        updateButtonStates();
    }

    // ---------------------------------------------------------------- toolbars

    private JPanel buildInterceptBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        masterToggle.setSelected(engine.isMasterOn());
        applyMasterToggleStyle();
        masterToggle.addActionListener(e -> {
            engine.setMasterOn(masterToggle.isSelected());
            applyMasterToggleStyle();
        });
        bar.add(masterToggle);

        interceptRequestsBox.addActionListener(e -> engine.setInterceptRequests(interceptRequestsBox.isSelected()));
        interceptResponsesBox.addActionListener(e -> engine.setInterceptResponses(interceptResponsesBox.isSelected()));
        bar.add(interceptRequestsBox);
        bar.add(interceptResponsesBox);

        automaticEditorBox.setSelected(engine.ruleEngine().isEnabled());
        automaticEditorBox.addActionListener(e -> {
            engine.ruleEngine().setEnabled(automaticEditorBox.isSelected());
            state.persistInterceptConfig();
        });
        bar.add(automaticEditorBox);

        JButton rulesButton = new JButton("Rules...");
        rulesButton.addActionListener(e -> ModificationRuleDialog.showDialog(this, state));
        bar.add(rulesButton);

        JButton breakOnButton = new JButton("Break On...");
        breakOnButton.addActionListener(e -> InterceptConditionDialog.showDialog(this, state));
        bar.add(breakOnButton);

        JButton forwardAllButton = new JButton("Forward All (safety)");
        forwardAllButton.setToolTipText("Immediately forwards every currently-held message as-is");
        forwardAllButton.addActionListener(e -> engine.forwardAllPending());
        bar.add(forwardAllButton);

        return bar;
    }

    private void applyMasterToggleStyle() {
        boolean on = masterToggle.isSelected();
        masterToggle.setText("INTERCEPT: " + (on ? "ON" : "OFF"));
        masterToggle.setBackground(on ? new Color(200, 60, 60) : new Color(230, 230, 230));
        masterToggle.setForeground(on ? Color.WHITE : Color.BLACK);
        masterToggle.setOpaque(true);
    }

    private JPanel buildActionBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        forwardButton.setToolTipText("Forward this message exactly as it was captured (ignores any edits made in the editor above)");
        forwardButton.addActionListener(e -> forward(false));
        forwardEditButton.setToolTipText("Forward whatever is currently in the Request/Response editor above (commits your edits)");
        forwardEditButton.addActionListener(e -> forward(true));
        dropButton.addActionListener(e -> drop());
        JButton sendToReplay = new JButton("Send to Replay");
        sendToReplay.addActionListener(e -> sendToReplay());
        JButton sendToWorkbench = new JButton("Send to Workbench");
        sendToWorkbench.addActionListener(e -> sendToWorkbench());
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> clearHistory());

        bar.add(forwardButton);
        bar.add(forwardEditButton);
        bar.add(dropButton);
        bar.add(sendToReplay);
        bar.add(sendToWorkbench);
        bar.add(clear);
        return bar;
    }

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        bar.add(new JLabel("Search:"));
        bar.add(searchField);
        bar.add(methodFilter);
        bar.add(statusFilter);
        bar.add(pinnedOnlyBox);
        return bar;
    }

    // ---------------------------------------------------------------- table wiring

    private void wireTableSelection() {
        table.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (e.getValueIsAdjusting()) return;
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                selected = null;
            } else {
                selected = tableModel.rowAt(table.convertRowIndexToModel(viewRow));
            }
            refreshEditorsFromSelected();
            updateButtonStates();
        });
    }

    private void wireTableContextMenu() {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { maybeShowMenu(e); }

            @Override
            public void mouseReleased(MouseEvent e) { maybeShowMenu(e); }

            private void maybeShowMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow < 0) return;
                table.setRowSelectionInterval(viewRow, viewRow);
                buildContextMenu().show(table, e.getX(), e.getY());
            }
        });
    }

    private JPopupMenu buildContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(menuItem("Forward", () -> forward(false)));
        menu.add(menuItem("Forward & Edit", () -> forward(true)));
        menu.add(menuItem("Drop", this::drop));
        menu.addSeparator();
        menu.add(menuItem("Send to Replay", this::sendToReplay));
        menu.add(menuItem("Send to Workbench", this::sendToWorkbench));
        menu.add(menuItem("Repeat (resend a fresh copy)", this::repeat));
        menu.addSeparator();
        menu.add(menuItem(selected != null && selected.isPinned() ? "Unpin" : "Pin", this::togglePin));
        menu.add(menuItem("Add/Edit Tag...", this::addTag));
        menu.add(menuItem("Add/Edit Note...", this::addNote));
        menu.addSeparator();
        menu.add(menuItem("Delete Row", this::deleteSelectedRow));
        return menu;
    }

    private JMenuItem menuItem(String label, Runnable action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(e -> action.run());
        return item;
    }

    private void wireFilters() {
        Runnable apply = this::applyFilter;
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { apply.run(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { apply.run(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { apply.run(); }
        });
        methodFilter.addActionListener(e -> apply.run());
        statusFilter.addActionListener(e -> apply.run());
        pinnedOnlyBox.addActionListener(e -> apply.run());
    }

    private void applyFilter() {
        String query = searchField.getText().trim();
        String method = (String) methodFilter.getSelectedItem();
        String status = (String) statusFilter.getSelectedItem();
        boolean pinnedOnly = pinnedOnlyBox.isSelected();

        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends InterceptTableModel, ? extends Integer> entry) {
                InterceptedMessage m = entry.getModel().rowAt(entry.getIdentifier());
                if (pinnedOnly && !m.isPinned()) return false;
                if (method != null && !method.startsWith("Any") && !method.equalsIgnoreCase(m.method())) return false;
                if (status != null && !status.startsWith("Any")) {
                    Integer sc = m.statusCode();
                    if (sc == null) return false;
                    char cls = status.charAt(0);
                    if (sc / 100 != Character.getNumericValue(cls)) return false;
                }
                if (!query.isBlank()) {
                    String haystack = (m.host() + " " + m.path() + " " + String.join(" ", m.tags()) + " " + m.notes()).toLowerCase();
                    try {
                        return Pattern.compile(query, Pattern.CASE_INSENSITIVE).matcher(haystack).find();
                    } catch (PatternSyntaxException ex) {
                        return haystack.contains(query.toLowerCase());
                    }
                }
                return true;
            }
        });
        updateCountLabel();
    }

    // ---------------------------------------------------------------- actions

    private void forward(boolean useEditorContent) {
        if (selected == null || selected.pendingDecision() == null || selected.pendingDecision().isDone()) {
            return;
        }
        InterceptDecision decision;
        if (selected.holdPhase() == InterceptedMessage.HoldPhase.REQUEST) {
            HttpRequest req = useEditorContent ? requestEditor.getRequest() : selected.currentRequest();
            if (useEditorContent) {
                // Forward & Edit commits whatever the analyst typed, including any {{VARIABLE}} placeholders - resolve them now, right before the request actually goes out.
                req = VariableResolver.resolveInRequest(req, state.variableStore());
            }
            decision = InterceptDecision.forwardRequest(req);
        } else {
            HttpResponse resp = useEditorContent ? responseEditor.getResponse() : selected.currentResponse();
            decision = InterceptDecision.forwardResponse(resp);
        }
        selected.pendingDecision().complete(decision);
    }

    private void drop() {
        if (selected == null || selected.holdPhase() != InterceptedMessage.HoldPhase.REQUEST) {
            return;
        }
        if (selected.pendingDecision() != null && !selected.pendingDecision().isDone()) {
            selected.pendingDecision().complete(InterceptDecision.drop());
        }
    }

    private void sendToReplay() {
        if (selected == null || selected.currentRequest() == null) return;
        HttpRequestResponse rr = toRequestResponse(selected);
        if (state.mainPanel() != null) {
            state.mainPanel().openInWorkbenchAndReplay(rr);
        }
    }

    private void sendToWorkbench() {
        if (selected == null || selected.currentRequest() == null) return;
        HttpRequestResponse rr = toRequestResponse(selected);
        if (state.mainPanel() != null) {
            state.mainPanel().openInWorkbench(rr);
        }
    }

    private void repeat() {
        if (selected == null || selected.currentRequest() == null) return;
        HttpRequest toSend = selected.currentRequest();
        new Thread(() -> {
            try {
                state.api().http().sendRequest(toSend);
            } catch (Exception ignored) {
            }
        }, "payload-extractor-intercept-repeat").start();
    }

    private void togglePin() {
        if (selected == null) return;
        selected.setPinned(!selected.isPinned());
        tableModel.updateRow(selected);
    }

    private void addTag() {
        if (selected == null) return;
        String tag = JOptionPane.showInputDialog(this, "Tag (e.g. Security Test, Interesting, Authentication, Authorization):",
                String.join(", ", selected.tags()));
        if (tag != null) {
            selected.tags().clear();
            for (String t : tag.split(",")) {
                if (!t.isBlank()) selected.tags().add(t.trim());
            }
            tableModel.updateRow(selected);
        }
    }

    private void addNote() {
        if (selected == null) return;
        String note = JOptionPane.showInputDialog(this, "Note:", selected.notes());
        if (note != null) {
            selected.setNotes(note);
            tableModel.updateRow(selected);
        }
    }

    private void deleteSelectedRow() {
        if (selected == null) return;
        engine.history().remove(selected);
        reload();
    }

    private void clearHistory() {
        engine.clearHistory();
        reload();
    }

    private void reload() {
        tableModel.clear(false);
        for (InterceptedMessage m : engine.history()) {
            tableModel.addRow(m);
        }
        updateCountLabel();
    }

    private void createRuleFromSelection(String selectedText, InterceptDirection direction) {
        if (selectedText == null || selectedText.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select some text in the editor first.", "Nothing selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ModificationRuleDialog.showDialogForSelection(this, state, selectedText, direction);
    }

    private void extractSelectionAsVariable(String selectedText) {
        if (selectedText == null || selectedText.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select some text in the editor first.", "Nothing selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String suggested = Variable.normalizeName(selected != null ? selected.path() : "");
        String name = (String) JOptionPane.showInputDialog(this, "Variable name (used as {{NAME}} in future requests):",
                "Extract as Variable", JOptionPane.PLAIN_MESSAGE, null, null, suggested);
        if (name == null || name.isBlank()) {
            return;
        }
        String host = selected != null ? selected.host() : null;
        Variable v = state.variableStore().upsert(name, selectedText, host);
        state.persistVariables();
        JOptionPane.showMessageDialog(this, "Saved as {{" + v.name() + "}}. Use it in any later request's path, headers, cookies, or body via Forward & Edit.",
                "Variable saved", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showDiff() {
        if (selected == null) return;
        JTextArea originalArea = new JTextArea(selected.originalRequest() != null ? selected.originalRequest().toString() : "");
        JTextArea modifiedArea = new JTextArea(selected.currentRequest() != null ? selected.currentRequest().toString() : "");
        for (JTextArea a : new JTextArea[]{originalArea, modifiedArea}) {
            a.setEditable(false);
            a.setLineWrap(true);
        }
        boolean identical = originalArea.getText().equals(modifiedArea.getText());

        JPanel panel = new JPanel(new GridLayout(1, 2, 8, 0));
        JPanel left = new JPanel(new BorderLayout());
        left.add(new JLabel("Original"), BorderLayout.NORTH);
        left.add(new JScrollPane(originalArea), BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout());
        JLabel modifiedLabel = new JLabel(identical ? "Modified (identical)" : "Modified (changed)");
        modifiedLabel.setForeground(identical ? Color.GRAY : new Color(170, 30, 30));
        right.add(modifiedLabel, BorderLayout.NORTH);
        right.add(new JScrollPane(modifiedArea), BorderLayout.CENTER);
        panel.add(left);
        panel.add(right);
        panel.setPreferredSize(new Dimension(900, 500));

        JOptionPane.showMessageDialog(this, panel, "Original vs Modified", JOptionPane.PLAIN_MESSAGE);
    }

    // ---------------------------------------------------------------- helpers

    private HttpRequestResponse toRequestResponse(InterceptedMessage msg) {
        HttpResponse resp = msg.currentResponse();
        return resp != null ? HttpRequestResponse.httpRequestResponse(msg.currentRequest(), resp)
                : HttpRequestResponse.httpRequestResponse(msg.currentRequest(), HttpResponse.httpResponse());
    }

    private void refreshEditorsFromSelected() {
        if (selected == null) {
            return;
        }
        if (selected.currentRequest() != null) {
            requestEditor.setRequest(selected.currentRequest());
        }
        responseEditor.setResponse(selected.currentResponse() != null ? selected.currentResponse() : HttpResponse.httpResponse());
    }

    private void updateButtonStates() {
        boolean waitingRequest = selected != null && selected.holdPhase() == InterceptedMessage.HoldPhase.REQUEST
                && selected.pendingDecision() != null && !selected.pendingDecision().isDone();
        boolean waitingResponse = selected != null && selected.holdPhase() == InterceptedMessage.HoldPhase.RESPONSE
                && selected.pendingDecision() != null && !selected.pendingDecision().isDone();
        forwardButton.setEnabled(waitingRequest || waitingResponse);
        forwardEditButton.setEnabled(waitingRequest || waitingResponse);
        dropButton.setEnabled(waitingRequest);
    }

    private void updateCountLabel() {
        countLabel.setText(table.getRowCount() + " of " + tableModel.getRowCount() + " message(s) shown");
    }

    // ---------------------------------------------------------------- InterceptEngine.Listener

    @Override
    public void onMessageAdded(InterceptedMessage msg) {
        tableModel.addRow(msg);
        updateCountLabel();
    }

    @Override
    public void onMessageUpdated(InterceptedMessage msg) {
        tableModel.updateRow(msg);
        if (selected != null && selected.id() == msg.id()) {
            refreshEditorsFromSelected();
            updateButtonStates();
        }
        updateCountLabel();
    }
}
