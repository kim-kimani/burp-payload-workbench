package com.cytonn.montoya.payloadextractor.ui;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.analysis.DiffResult;
import com.cytonn.montoya.payloadextractor.analysis.ResponseDiff;
import com.cytonn.montoya.payloadextractor.analysis.ResponseSizeStats;
import com.cytonn.montoya.payloadextractor.db.ImportExportManager;
import com.cytonn.montoya.payloadextractor.db.PayloadCollection;
import com.cytonn.montoya.payloadextractor.db.PayloadValue;
import com.cytonn.montoya.payloadextractor.history.HistoryEntry;
import com.cytonn.montoya.payloadextractor.mutation.VariationGenerator;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;
import com.cytonn.montoya.payloadextractor.replay.ReplayConfig;
import com.cytonn.montoya.payloadextractor.replay.ReplayEngine;
import com.cytonn.montoya.payloadextractor.replay.ReplayListener;
import com.cytonn.montoya.payloadextractor.replay.ReplayOrder;
import com.cytonn.montoya.payloadextractor.replay.ReplayStepResult;
import com.cytonn.montoya.payloadextractor.util.ResponseSizeFormatter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configures and runs a replay for one field: pick where the payload values come from (a fresh
 * Generator run, a remembered {@link PayloadCollection}, a loaded wordlist file, or a set of
 * boundary "variations" of the current value), how many parallel requests to fire, and an optional
 * stop-on-status guard - then streams live results with the same visual language as Workbench/
 * Intercept (colored status text, reused Montoya request/response editors for the selected step)
 * rather than a plain black-and-white table, plus a comparison/analysis toolbar (item 7/8).
 */
public final class ReplayConfigDialog extends JDialog {

    /** One row of the results table plus the extra per-row bookkeeping (pin/tags/notes/diff) the results table alone can't hold. */
    private final class ResultRow {
        final int rowId;
        final ReplayStepResult result;
        boolean pinned;
        String tags = "";
        String notes = "";
        DiffResult diffVsBaseline;

        ResultRow(int rowId, ReplayStepResult result) {
            this.rowId = rowId;
            this.result = result;
        }
    }

    private final ExtensionState state;
    private final HttpRequest baseRequest;
    private final ParsedField targetField;

    private final JRadioButton generateSource = new JRadioButton("Generate new values", true);
    private final JRadioButton collectionSource = new JRadioButton("Use remembered collection");
    private final JRadioButton fileSource = new JRadioButton("Load from file");
    private final JRadioButton variationsSource = new JRadioButton("Smart variations of current value");
    private final JComboBox<PayloadCollection> collectionCombo = new JComboBox<>();
    private final JLabel valuesSummaryLabel = new JLabel("No values chosen yet");
    private List<String> chosenValues = new ArrayList<>();

    private final JCheckBox parallelCheckbox = new JCheckBox("Send requests in parallel");
    private final JSpinner concurrencySpinner = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
    private final JComboBox<ReplayOrder> orderCombo = new JComboBox<>(ReplayOrder.values());
    private final JCheckBox stopOnStatusCheckbox = new JCheckBox("Stop as soon as status equals:");
    private final JSpinner stopStatusSpinner = new JSpinner(new SpinnerNumberModel(200, 100, 599, 1));
    private final JCheckBox maxRequestsCheckbox = new JCheckBox("Limit to first N requests:");
    private final JSpinner maxRequestsSpinner = new JSpinner(new SpinnerNumberModel(50, 1, 1000000, 1));
    private final JSpinner delaySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 60000, 50));

    private static final String[] RESULT_COLUMNS = {"Pin", "#", "Value", "Status", "Response Size", "ms", "Flag", "Tags", "Notes", "Error"};
    private final DefaultTableModel resultsModel = new DefaultTableModel(RESULT_COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable resultsTable = new JTable(resultsModel);
    private final List<ResultRow> rows = new ArrayList<>();
    private int nextRowId = 1;
    private final ResponseSizeStats sizeStats = new ResponseSizeStats();
    private final JLabel statsLabel = new JLabel("No responses yet");

    private final HttpRequestEditor detailRequestEditor;
    private final HttpResponseEditor detailResponseEditor;

    private final JLabel progressLabel = new JLabel("Idle");
    private final JButton startButton = new JButton("Start");
    private final JButton pauseButton = new JButton("Pause");
    private final JButton resumeButton = new JButton("Resume");
    private final JButton stopButton = new JButton("Stop");

    private ReplayEngine engine;

    public static void showDialog(Component parent, ExtensionState state, HttpRequest baseRequest, ParsedField targetField) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        ReplayConfigDialog dialog = new ReplayConfigDialog(owner, state, baseRequest, targetField);
        dialog.setVisible(true);
    }

    private ReplayConfigDialog(Window owner, ExtensionState state, HttpRequest baseRequest, ParsedField targetField) {
        super(owner, "Replay - " + targetField.name(), ModalityType.MODELESS);
        this.state = state;
        this.baseRequest = baseRequest;
        this.targetField = targetField;
        setLayout(new BorderLayout(8, 8));

        detailRequestEditor = state.api().userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        detailResponseEditor = state.api().userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);

        ButtonGroup group = new ButtonGroup();
        group.add(generateSource);
        group.add(collectionSource);
        group.add(fileSource);
        group.add(variationsSource);

        for (PayloadCollection c : state.database().allCollections()) {
            collectionCombo.addItem(c);
        }

        JPanel sourcePanel = new JPanel();
        sourcePanel.setLayout(new BoxLayout(sourcePanel, BoxLayout.Y_AXIS));
        sourcePanel.setBorder(BorderFactory.createTitledBorder("Payload value source"));

        JPanel genRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        genRow.add(generateSource);
        JButton configureGenButton = new JButton("Configure Generator...");
        configureGenButton.addActionListener(e -> {
            generateSource.setSelected(true);
            chosenValues = GeneratorDialog.showDialog(this, state, targetField);
            valuesSummaryLabel.setText(chosenValues.size() + " generated value(s)");
        });
        genRow.add(configureGenButton);
        sourcePanel.add(genRow);

        JPanel collRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        collRow.add(collectionSource);
        collRow.add(collectionCombo);
        JButton useCollectionButton = new JButton("Use This Collection");
        useCollectionButton.addActionListener(e -> {
            collectionSource.setSelected(true);
            PayloadCollection c = (PayloadCollection) collectionCombo.getSelectedItem();
            chosenValues = c == null ? List.of() : c.values().stream().map(PayloadValue::value).toList();
            valuesSummaryLabel.setText(chosenValues.size() + " value(s) from \"" + (c == null ? "" : c.normalizedName()) + "\"");
        });
        collRow.add(useCollectionButton);
        sourcePanel.add(collRow);

        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fileRow.add(fileSource);
        JButton loadFileButton = new JButton("Load File...");
        loadFileButton.addActionListener(e -> {
            fileSource.setSelected(true);
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Load payload wordlist (one value per line)");
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                try {
                    chosenValues = ImportExportManager.loadWordlist(Path.of(f.getAbsolutePath()));
                    valuesSummaryLabel.setText(chosenValues.size() + " value(s) from " + f.getName());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Could not read file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        fileRow.add(loadFileButton);
        sourcePanel.add(fileRow);

        JPanel variationsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        variationsRow.add(variationsSource);
        JButton useVariationsButton = new JButton("Generate Variations");
        useVariationsButton.setToolTipText("Boundary/neighbor values derived from the field's current value - e.g. 555 -> 556, 554, 0, -1, \"\"");
        useVariationsButton.addActionListener(e -> {
            variationsSource.setSelected(true);
            chosenValues = VariationGenerator.variationsFor(targetField.currentValue());
            valuesSummaryLabel.setText(chosenValues.size() + " variation(s) of \"" + targetField.currentValue() + "\"");
        });
        variationsRow.add(useVariationsButton);
        sourcePanel.add(variationsRow);
        sourcePanel.add(valuesSummaryLabel);

        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createTitledBorder("Send options - make it powerful"));

        JPanel parallelRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        parallelRow.add(parallelCheckbox);
        parallelRow.add(new JLabel("Concurrency:"));
        parallelRow.add(concurrencySpinner);
        optionsPanel.add(parallelRow);

        JPanel orderRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        orderRow.add(new JLabel("Order:"));
        orderRow.add(orderCombo);
        orderRow.add(new JLabel("Delay between requests (ms, sequential only):"));
        orderRow.add(delaySpinner);
        optionsPanel.add(orderRow);

        JPanel stopRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        stopRow.add(stopOnStatusCheckbox);
        stopRow.add(stopStatusSpinner);
        optionsPanel.add(stopRow);

        JPanel maxRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        maxRow.add(maxRequestsCheckbox);
        maxRow.add(maxRequestsSpinner);
        optionsPanel.add(maxRow);

        JPanel top = new JPanel(new BorderLayout());
        top.add(sourcePanel, BorderLayout.NORTH);
        top.add(optionsPanel, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        // ---- results: colored table + analysis toolbar + stats, over reused request/response editors for the selected step
        installColoredRenderers();
        resultsTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshDetailEditors();
        });

        JPanel resultsPanel = new JPanel(new BorderLayout(4, 4));
        resultsPanel.add(new JScrollPane(resultsTable), BorderLayout.CENTER);
        resultsPanel.add(buildResultsToolbar(), BorderLayout.SOUTH);
        JPanel resultsWithStats = new JPanel(new BorderLayout(4, 4));
        resultsWithStats.add(resultsPanel, BorderLayout.CENTER);
        resultsWithStats.add(statsLabel, BorderLayout.NORTH);

        JPanel detailRequestPanel = new JPanel(new BorderLayout());
        detailRequestPanel.setBorder(BorderFactory.createTitledBorder("Request (selected step)"));
        detailRequestPanel.add(detailRequestEditor.uiComponent(), BorderLayout.CENTER);
        JPanel detailResponsePanel = new JPanel(new BorderLayout());
        detailResponsePanel.setBorder(BorderFactory.createTitledBorder("Response (selected step)"));
        detailResponsePanel.add(detailResponseEditor.uiComponent(), BorderLayout.CENTER);
        JSplitPane detailSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, detailRequestPanel, detailResponsePanel);
        detailSplit.setResizeWeight(0.5);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, resultsWithStats, detailSplit);
        centerSplit.setResizeWeight(0.45);
        add(centerSplit, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(progressLabel, BorderLayout.WEST);
        JPanel controlButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pauseButton.setEnabled(false);
        resumeButton.setEnabled(false);
        stopButton.setEnabled(false);
        startButton.addActionListener(e -> start());
        pauseButton.addActionListener(e -> { if (engine != null) engine.pause(); });
        resumeButton.addActionListener(e -> { if (engine != null) engine.resume(); });
        stopButton.addActionListener(e -> { if (engine != null) engine.stop(); });
        controlButtons.add(startButton);
        controlButtons.add(pauseButton);
        controlButtons.add(resumeButton);
        controlButtons.add(stopButton);
        bottom.add(controlButtons, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        setSize(980, 760);
        setLocationRelativeTo(owner);
    }

    // ---------------------------------------------------------------- visual consistency (item 8)

    /** Status gets the same colored-bold treatment as everywhere else (Intercept/History); Flag is colored when a step is diff-flagged vs the run's baseline (item 7). */
    private void installColoredRenderers() {
        int statusCol = indexOf("Status");
        resultsTable.getColumnModel().getColumn(statusCol).setCellRenderer(StyleKit.coloredCellRenderer(v -> {
            try { return StyleKit.statusColor(v == null ? null : Integer.parseInt(v.toString())); } catch (NumberFormatException e) { return StyleKit.neutral(); }
        }));
        int flagCol = indexOf("Flag");
        resultsTable.getColumnModel().getColumn(flagCol).setCellRenderer(StyleKit.coloredCellRenderer(v ->
                v != null && !v.toString().isBlank() ? StyleKit.interestingColor() : StyleKit.neutral()));
    }

    private static int indexOf(String column) {
        for (int i = 0; i < RESULT_COLUMNS.length; i++) {
            if (RESULT_COLUMNS[i].equals(column)) return i;
        }
        return -1;
    }

    // ---------------------------------------------------------------- run

    private void start() {
        if (chosenValues.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Choose a payload value source first (generate, collection, file, or variations).", "No values", JOptionPane.WARNING_MESSAGE);
            return;
        }
        resultsModel.setRowCount(0);
        rows.clear();
        nextRowId = 1;
        sizeStats.reset();
        statsLabel.setText("No responses yet");

        ReplayConfig.Builder builder = ReplayConfig.builder()
                .baseRequest(baseRequest)
                .targetField(targetField)
                .payloadValues(chosenValues)
                .order((ReplayOrder) orderCombo.getSelectedItem())
                .parallel(parallelCheckbox.isSelected())
                .concurrency((Integer) concurrencySpinner.getValue())
                .delayMillisBetweenRequests(((Number) delaySpinner.getValue()).longValue());
        if (stopOnStatusCheckbox.isSelected()) {
            builder.stopOnStatusCode((Integer) stopStatusSpinner.getValue());
        }
        if (maxRequestsCheckbox.isSelected()) {
            builder.maxRequests((Integer) maxRequestsSpinner.getValue());
        }
        ReplayConfig config = builder.build();

        engine = new ReplayEngine(state.api().http(), new ReplayListener() {
            @Override
            public void onStarted(int totalSteps) {
                SwingUtilities.invokeLater(() -> {
                    progressLabel.setText("Running: 0 / " + totalSteps);
                    startButton.setEnabled(false);
                    pauseButton.setEnabled(true);
                    stopButton.setEnabled(true);
                });
            }

            @Override
            public void onStepCompleted(ReplayStepResult result) {
                SwingUtilities.invokeLater(() -> {
                    addResultRow(result);
                    progressLabel.setText("Running: " + resultsModel.getRowCount());
                    String host = (result.requestResponse() != null && result.requestResponse().httpService() != null)
                            ? result.requestResponse().httpService().host() : null;
                    state.historyManager().record(HistoryEntry.of(HistoryEntry.Action.REPLAY_STEP, targetField.name(),
                            targetField.location(), targetField.originalValue(), result.payloadValue(),
                            "replay", System.currentTimeMillis(), host, result.statusCode())
                            .withResponseSizeBytes(result.responseSizeBytes()));
                });
            }

            @Override
            public void onPaused() {
                SwingUtilities.invokeLater(() -> {
                    pauseButton.setEnabled(false);
                    resumeButton.setEnabled(true);
                });
            }

            @Override
            public void onResumed() {
                SwingUtilities.invokeLater(() -> {
                    pauseButton.setEnabled(true);
                    resumeButton.setEnabled(false);
                });
            }

            @Override
            public void onStopped(String reason) {
                SwingUtilities.invokeLater(() -> {
                    progressLabel.setText("Stopped: " + reason);
                    resetButtons();
                });
            }

            @Override
            public void onCompleted() {
                SwingUtilities.invokeLater(() -> {
                    progressLabel.setText("Completed (" + resultsModel.getRowCount() + " requests)");
                    resetButtons();
                });
            }
        }, state.variableStore());

        Thread worker = new Thread(() -> engine.run(config), "payload-extractor-replay");
        worker.setDaemon(true);
        worker.start();
    }

    private void resetButtons() {
        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
        resumeButton.setEnabled(false);
        stopButton.setEnabled(false);
    }

    // ---------------------------------------------------------------- results table (item 7/8)

    private void addResultRow(ReplayStepResult result) {
        ResultRow row = new ResultRow(nextRowId++, result);
        HttpResponse baseline = rows.stream()
                .map(r -> r.result.requestResponse())
                .filter(rr -> rr != null && rr.hasResponse())
                .map(HttpRequestResponse::response)
                .findFirst().orElse(null);
        if (baseline != null && result.requestResponse() != null && result.requestResponse().hasResponse()) {
            row.diffVsBaseline = ResponseDiff.compare(baseline, result.requestResponse().response());
        }
        rows.add(row);
        if (result.responseSizeBytes() != null) {
            sizeStats.add(result.responseSizeBytes());
        }
        statsLabel.setText("Response size: " + sizeStats.summary());
        appendTableRow(row);
    }

    private void appendTableRow(ResultRow row) {
        ReplayStepResult r = row.result;
        resultsModel.addRow(new Object[]{
                row.pinned ? "★" : "",
                r.stepIndex(),
                r.payloadValue(),
                r.statusCode() == null ? "" : r.statusCode(),
                ResponseSizeFormatter.format(r.responseSizeBytes()),
                r.roundTripMillis(),
                (row.diffVsBaseline != null && row.diffVsBaseline.interesting) ? "⚑ " + row.diffVsBaseline.summary() : "",
                row.tags,
                row.notes,
                r.error() == null ? "" : r.error()
        });
    }

    private void rerenderTable() {
        int selected = resultsTable.getSelectedRow();
        resultsModel.setRowCount(0);
        for (ResultRow row : rows) {
            appendTableRow(row);
        }
        if (selected >= 0 && selected < resultsModel.getRowCount()) {
            resultsTable.setRowSelectionInterval(selected, selected);
        }
    }

    private List<ResultRow> selectedRows() {
        List<ResultRow> out = new ArrayList<>();
        for (int viewRow : resultsTable.getSelectedRows()) {
            int modelRow = resultsTable.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < rows.size()) {
                out.add(rows.get(modelRow));
            }
        }
        return out;
    }

    private void refreshDetailEditors() {
        List<ResultRow> selected = selectedRows();
        if (selected.size() != 1 || selected.get(0).result.requestResponse() == null) {
            return;
        }
        HttpRequestResponse rr = selected.get(0).result.requestResponse();
        detailRequestEditor.setRequest(rr.request());
        detailResponseEditor.setResponse(rr.hasResponse() ? rr.response() : HttpResponse.httpResponse());
    }

    // ---------------------------------------------------------------- results toolbar (items 7/8/20)

    private JPanel buildResultsToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        JButton compare = new JButton("Compare Selected (2)");
        compare.setToolTipText("Select exactly two rows to see their full status/size/header/JSON-field differences");
        compare.addActionListener(e -> compareSelected());
        bar.add(compare);

        JButton variations = new JButton("Generate Variations From Selected");
        variations.addActionListener(e -> variationsFromSelected());
        bar.add(variations);

        JButton sendWorkbench = new JButton("Send to Workbench");
        sendWorkbench.addActionListener(e -> sendSelectedToWorkbench());
        bar.add(sendWorkbench);

        JButton sendIntercept = new JButton("Send to Intercept");
        sendIntercept.setToolTipText("Adds this request/response to the Intercept tab's REQUEST HISTORY for tagging/pinning/inspection there too");
        sendIntercept.addActionListener(e -> sendSelectedToIntercept());
        bar.add(sendIntercept);

        JButton repeat = new JButton("Repeat");
        repeat.setToolTipText("Resend this exact request again (also covers \"Clone\" - a fresh copy of the same request)");
        repeat.addActionListener(e -> repeatSelected());
        bar.add(repeat);

        JButton pin = new JButton("Pin/Unpin");
        pin.addActionListener(e -> togglePinSelected());
        bar.add(pin);

        JButton noteTag = new JButton("Add Note/Tag...");
        noteTag.addActionListener(e -> addNoteTagSelected());
        bar.add(noteTag);

        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> deleteSelected());
        bar.add(delete);

        JButton export = new JButton("Export CSV");
        export.addActionListener(e -> exportResults());
        bar.add(export);

        return bar;
    }

    private void compareSelected() {
        List<ResultRow> selected = selectedRows();
        if (selected.size() != 2) {
            JOptionPane.showMessageDialog(this, "Select exactly two rows first.", "Compare Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        HttpRequestResponse a = selected.get(0).result.requestResponse();
        HttpRequestResponse b = selected.get(1).result.requestResponse();
        if (a == null || b == null || !a.hasResponse() || !b.hasResponse()) {
            JOptionPane.showMessageDialog(this, "Both selected steps need a response to compare.", "Compare Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ResponseDiffDialog.showDialog(this, state, "\"" + selected.get(0).result.payloadValue() + "\"", a.response(),
                "\"" + selected.get(1).result.payloadValue() + "\"", b.response());
    }

    private void variationsFromSelected() {
        List<ResultRow> selected = selectedRows();
        if (selected.size() != 1) {
            JOptionPane.showMessageDialog(this, "Select exactly one row first.", "Generate Variations", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String value = selected.get(0).result.payloadValue();
        variationsSource.setSelected(true);
        chosenValues = VariationGenerator.variationsFor(value);
        valuesSummaryLabel.setText(chosenValues.size() + " variation(s) of \"" + value + "\" - click Start to run them");
    }

    private void sendSelectedToWorkbench() {
        List<ResultRow> selected = selectedRows();
        if (selected.size() != 1 || selected.get(0).result.requestResponse() == null) {
            JOptionPane.showMessageDialog(this, "Select exactly one row with a request first.", "Send to Workbench", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (state.mainPanel() != null) {
            state.mainPanel().openInWorkbench(selected.get(0).result.requestResponse());
        }
    }

    private void sendSelectedToIntercept() {
        List<ResultRow> selected = selectedRows();
        if (selected.size() != 1 || selected.get(0).result.requestResponse() == null) {
            JOptionPane.showMessageDialog(this, "Select exactly one row with a request first.", "Send to Intercept", JOptionPane.WARNING_MESSAGE);
            return;
        }
        HttpRequestResponse rr = selected.get(0).result.requestResponse();
        String host = rr.httpService() != null ? rr.httpService().host() : null;
        state.interceptEngine().addObserved(rr.request(), rr.hasResponse() ? rr.response() : null, host);
        JOptionPane.showMessageDialog(this, "Added to the Intercept tab's REQUEST HISTORY.", "Sent", JOptionPane.INFORMATION_MESSAGE);
    }

    private void repeatSelected() {
        List<ResultRow> selected = selectedRows();
        if (selected.size() != 1 || selected.get(0).result.requestResponse() == null) {
            JOptionPane.showMessageDialog(this, "Select exactly one row with a request first.", "Repeat", JOptionPane.WARNING_MESSAGE);
            return;
        }
        HttpRequest toSend = selected.get(0).result.requestResponse().request();
        String value = selected.get(0).result.payloadValue();
        new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                HttpRequestResponse rr = state.api().http().sendRequest(toSend);
                long elapsed = System.currentTimeMillis() - start;
                ReplayStepResult repeated = ReplayStepResult.success(rows.size(), value + " (repeat)", rr, elapsed);
                SwingUtilities.invokeLater(() -> addResultRow(repeated));
            } catch (Exception ex) {
                ReplayStepResult failed = ReplayStepResult.failure(rows.size(), value + " (repeat)", ex.getMessage());
                SwingUtilities.invokeLater(() -> addResultRow(failed));
            }
        }, "payload-extractor-replay-repeat").start();
    }

    private void togglePinSelected() {
        for (ResultRow row : selectedRows()) {
            row.pinned = !row.pinned;
        }
        rerenderTable();
    }

    private void addNoteTagSelected() {
        List<ResultRow> selected = selectedRows();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one row first.", "Add Note/Tag", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JTextField tagField = new JTextField(selected.size() == 1 ? selected.get(0).tags : "", 20);
        JTextField noteField = new JTextField(selected.size() == 1 ? selected.get(0).notes : "", 20);
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(labeled("Tag:", tagField));
        form.add(labeled("Note:", noteField));
        int result = JOptionPane.showConfirmDialog(this, form, "Add Note/Tag", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            for (ResultRow row : selected) {
                row.tags = tagField.getText();
                row.notes = noteField.getText();
            }
            rerenderTable();
        }
    }

    private void deleteSelected() {
        List<ResultRow> selected = selectedRows();
        rows.removeAll(selected);
        rerenderTable();
    }

    private void exportResults() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export replay results (CSV)");
        chooser.setSelectedFile(new File("replay-results-" + targetField.name() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder("#,Value,Status,Response Size,ms,Flag,Tags,Notes,Error\n");
            for (ResultRow row : rows) {
                ReplayStepResult r = row.result;
                sb.append(r.stepIndex()).append(',')
                  .append(csv(r.payloadValue())).append(',')
                  .append(csv(r.statusCode() == null ? "" : r.statusCode().toString())).append(',')
                  .append(csv(ResponseSizeFormatter.format(r.responseSizeBytes()))).append(',')
                  .append(r.roundTripMillis()).append(',')
                  .append(csv(row.diffVsBaseline != null && row.diffVsBaseline.interesting ? row.diffVsBaseline.summary() : "")).append(',')
                  .append(csv(row.tags)).append(',')
                  .append(csv(row.notes)).append(',')
                  .append(csv(r.error() == null ? "" : r.error()))
                  .append('\n');
            }
            Files.writeString(Path.of(chooser.getSelectedFile().getAbsolutePath()), sb.toString(), StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Exported.", "Done", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private static JPanel labeled(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(480, 28));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(60, 20));
        p.add(l, BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }
}
