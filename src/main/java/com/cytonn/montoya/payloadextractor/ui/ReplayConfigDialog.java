package com.cytonn.montoya.payloadextractor.ui;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.cytonn.montoya.payloadextractor.ExtensionState;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Configures and runs a replay for one field: pick where the payload values come from (a fresh
 * Generator run, a remembered {@link PayloadCollection}, or a loaded wordlist file), how many
 * parallel requests to fire, and an optional stop-on-status guard - then streams live results.
 */
public final class ReplayConfigDialog extends JDialog {

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

    private final DefaultTableModel resultsModel = new DefaultTableModel(new Object[]{"#", "Value", "Status", "Response Size", "ms", "Error"}, 0);
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

        JTable resultsTable = new JTable(resultsModel);
        add(new JScrollPane(resultsTable), BorderLayout.CENTER);

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

        setSize(760, 560);
        setLocationRelativeTo(owner);
    }

    private void start() {
        if (chosenValues.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Choose a payload value source first (generate, collection, or file).", "No values", JOptionPane.WARNING_MESSAGE);
            return;
        }
        resultsModel.setRowCount(0);

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
                    resultsModel.addRow(new Object[]{
                            result.stepIndex(),
                            result.payloadValue(),
                            result.statusCode() == null ? "-" : result.statusCode(),
                            ResponseSizeFormatter.format(result.responseSizeBytes()),
                            result.roundTripMillis(),
                            result.error() == null ? "" : result.error()
                    });
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
}
