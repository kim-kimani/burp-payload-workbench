package com.cytonn.montoya.payloadextractor.ui;

import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.db.ImportExportManager;
import com.cytonn.montoya.payloadextractor.generator.GeneratorKind;
import com.cytonn.montoya.payloadextractor.generator.GeneratorParams;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for configuring and previewing a {@link GeneratorKind} - shared by the Workbench's
 * per-field "Gen" (quick single-value fill) action and the Replay dialog's payload-list source.
 * Includes the OTP-style {@code length} option ("6 digits, random or sequential from 0, keep the
 * length") and the free-form Custom Script field.
 */
public final class GeneratorDialog extends JDialog {

    private final ExtensionState state;
    private List<String> result = List.of();
    private boolean confirmed = false;

    private final JComboBox<GeneratorKind> kindCombo = new JComboBox<>(GeneratorKind.values());
    private final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100000, 1));
    private final JCheckBox useLengthCheckbox = new JCheckBox("Fixed length (OTP-style, zero-padded)");
    private final JSpinner lengthSpinner = new JSpinner(new SpinnerNumberModel(6, 1, 32, 1));
    private final JSpinner minSpinner = new JSpinner(new SpinnerNumberModel(0, Long.MIN_VALUE, Long.MAX_VALUE, 1));
    private final JSpinner maxSpinner = new JSpinner(new SpinnerNumberModel(999999, Long.MIN_VALUE, Long.MAX_VALUE, 1));
    private final JCheckBox uniqueCheckbox = new JCheckBox("Unique values", true);
    private final JTextField charsetField = new JTextField("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
    private final JTextField patternField = new JTextField("USER-{digit:4}-{alpha:2}");
    private final JTextField regexField = new JTextField("[a-z]{5}[0-9]{3}");
    private final JTextField prefixField = new JTextField();
    private final JTextField suffixField = new JTextField();
    private final JTextArea scriptArea = new JTextArea(6, 30);
    private final JLabel wordlistFileLabel = new JLabel("No file loaded");
    private final JList<String> previewList = new JList<>();
    private List<String> loadedWordlist = new ArrayList<>();

    public static List<String> showDialog(Component parent, ExtensionState state, ParsedField field) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        GeneratorDialog dialog = new GeneratorDialog(owner, state, field);
        dialog.setVisible(true);
        return dialog.confirmed ? dialog.result : List.of();
    }

    private GeneratorDialog(Window owner, ExtensionState state, ParsedField field) {
        super(owner, "Generate Payload Values" + (field != null ? " - " + field.name() : ""), ModalityType.APPLICATION_MODAL);
        this.state = state;
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel kindRow = row("Generator:", kindCombo);
        form.add(kindRow);
        form.add(row("Count:", countSpinner));

        JPanel lengthRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        lengthRow.add(useLengthCheckbox);
        lengthRow.add(new JLabel("Length:"));
        lengthRow.add(lengthSpinner);
        form.add(lengthRow);

        form.add(row("Min:", minSpinner));
        form.add(row("Max:", maxSpinner));
        form.add(row("", uniqueCheckbox));
        form.add(row("Charset:", charsetField));
        form.add(row("Pattern:", patternField));
        form.add(row("Regex:", regexField));
        form.add(row("Prefix:", prefixField));
        form.add(row("Suffix:", suffixField));

        JPanel wordlistRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadFileButton = new JButton("Load File...");
        loadFileButton.addActionListener(e -> loadWordlistFile());
        wordlistRow.add(loadFileButton);
        wordlistRow.add(wordlistFileLabel);
        form.add(wordlistRow);

        form.add(new JLabel("Custom Script (type what you want - see tooltip for supported syntax):"));
        scriptArea.setToolTipText("A tiny scripting language: statements separated by ';' or newlines, last one is the value. "
                + "Built-ins: index, count. Functions: pad(v,len), upper(s), lower(s), len(s), substr(s,a,b), "
                + "randInt(min,max), randStr(len), concat(a,b,...), replace(s,from,to), now(). "
                + "Example: otp = pad(randInt(0,999999), 6); \"OTP-\" + otp");
        scriptArea.setLineWrap(true);
        form.add(new JScrollPane(scriptArea));

        JScrollPane formScroll = new JScrollPane(form);
        formScroll.setPreferredSize(new Dimension(480, 420));
        add(formScroll, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.setBorder(BorderFactory.createTitledBorder("Preview"));
        right.setPreferredSize(new Dimension(220, 420));
        right.add(new JScrollPane(previewList), BorderLayout.CENTER);
        JButton previewButton = new JButton("Generate Preview");
        previewButton.addActionListener(e -> runGeneration());
        right.add(previewButton, BorderLayout.SOUTH);
        add(right, BorderLayout.EAST);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("Use These Values");
        ok.addActionListener(e -> {
            runGeneration();
            confirmed = true;
            setVisible(false);
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> setVisible(false));
        buttons.add(ok);
        buttons.add(cancel);
        add(buttons, BorderLayout.SOUTH);

        if (field != null) {
            String cat = field.category();
            if ("OTP".equals(cat)) {
                kindCombo.setSelectedItem(GeneratorKind.RANDOM_INTEGER);
                useLengthCheckbox.setSelected(true);
            }
        }

        pack();
        setLocationRelativeTo(owner);
    }

    private void loadWordlistFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load payload wordlist file (one value per line)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            try {
                loadedWordlist = ImportExportManager.loadWordlist(Path.of(f.getAbsolutePath()));
                wordlistFileLabel.setText(f.getName() + " (" + loadedWordlist.size() + " values)");
                kindCombo.setSelectedItem(GeneratorKind.WORDLIST);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Could not read file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void runGeneration() {
        GeneratorKind kind = (GeneratorKind) kindCombo.getSelectedItem();
        GeneratorParams params = new GeneratorParams()
                .count((Integer) countSpinner.getValue())
                .length(useLengthCheckbox.isSelected() ? (Integer) lengthSpinner.getValue() : null)
                .min(((Number) minSpinner.getValue()).longValue())
                .max(((Number) maxSpinner.getValue()).longValue())
                .unique(uniqueCheckbox.isSelected())
                .charset(charsetField.getText())
                .pattern(patternField.getText())
                .regex(regexField.getText())
                .prefix(prefixField.getText())
                .suffix(suffixField.getText())
                .wordlistValues(loadedWordlist)
                .customScript(scriptArea.getText());
        try {
            result = state.generatorRegistry().generate(kind, params);
        } catch (Exception ex) {
            result = List.of();
            JOptionPane.showMessageDialog(this, "Generation failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        previewList.setListData(result.toArray(new String[0]));
    }

    private static JPanel row(String label, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(60, 20));
        panel.add(l, BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }
}
