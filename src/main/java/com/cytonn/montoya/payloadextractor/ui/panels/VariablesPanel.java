package com.cytonn.montoya.payloadextractor.ui.panels;

import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.generator.GeneratorKind;
import com.cytonn.montoya.payloadextractor.generator.GeneratorParams;
import com.cytonn.montoya.payloadextractor.variables.ValueTracker;
import com.cytonn.montoya.payloadextractor.variables.Variable;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * "Variables & Value Tracking" (item 5): a table of every named {@code {{VARIABLE}}} the analyst
 * has extracted (from a Workbench field, a response value, or an Intercept editor selection - see
 * {@code WorkbenchPanel}/{@code InterceptPanel}), with Add/Edit/Delete and a couple of the new
 * built-in generators for quickly minting a value without leaving this tab. Also hosts "Track
 * Value" - a read-only search across History, Intercept history, and Payload Collections for every
 * place a given value has been seen (see {@link ValueTracker}).
 */
public final class VariablesPanel extends JPanel {

    private final ExtensionState state;
    private final DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"Name", "Value", "Source Host", "Updated"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public VariablesPanel(ExtensionState state) {
        super(new BorderLayout(8, 8));
        this.state = state;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel helper = new JLabel("<html>Use <b>{{NAME}}</b> anywhere in a request's path, headers, cookies, or body in the "
                + "Workbench or Intercept - it resolves to the value below right before the request is sent. "
                + "<b>{{UUID}}</b>, <b>{{TIMESTAMP}}</b>, and <b>{{RANDOM}}</b> always resolve dynamically, no need to add them here.</html>");
        add(helper, BorderLayout.NORTH);

        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add Variable...");
        add.addActionListener(e -> addVariable());
        JButton edit = new JButton("Edit...");
        edit.addActionListener(e -> editSelected());
        JButton generate = new JButton("Generate Value...");
        generate.setToolTipText("Mint a value with one of the built-in generators (UUID, Hex, Base64, Email, Phone, ...) and save it as a variable");
        generate.addActionListener(e -> generateVariable());
        JButton remove = new JButton("Delete");
        remove.addActionListener(e -> deleteSelected());
        JButton track = new JButton("Track Value...");
        track.setToolTipText("Search History, Intercept history, and Payload Collections for everywhere a value has appeared");
        track.addActionListener(e -> trackValue());
        buttons.add(add);
        buttons.add(edit);
        buttons.add(generate);
        buttons.add(remove);
        buttons.add(track);
        add(buttons, BorderLayout.SOUTH);

        refresh();
    }

    public void refresh() {
        tableModel.setRowCount(0);
        for (Variable v : state.variableStore().all()) {
            tableModel.addRow(new Object[]{
                    "{{" + v.name() + "}}",
                    v.value(),
                    v.sourceHost() == null ? "" : v.sourceHost(),
                    v.updatedEpochMillis() > 0 ? timeFormat.format(new Date(v.updatedEpochMillis())) : ""
            });
        }
    }

    private Variable selectedVariable() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        List<Variable> all = state.variableStore().all();
        int modelRow = table.convertRowIndexToModel(row);
        return modelRow >= 0 && modelRow < all.size() ? all.get(modelRow) : null;
    }

    private void addVariable() {
        JTextField nameField = new JTextField(20);
        JTextField valueField = new JTextField(20);
        JPanel form = form(nameField, valueField);
        int result = JOptionPane.showConfirmDialog(this, form, "Add Variable", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION && !nameField.getText().isBlank()) {
            state.variableStore().upsert(nameField.getText(), valueField.getText(), null);
            state.persistVariables();
            refresh();
        }
    }

    private void editSelected() {
        Variable v = selectedVariable();
        if (v == null) return;
        JTextField nameField = new JTextField(v.name(), 20);
        JTextField valueField = new JTextField(v.value(), 20);
        JPanel form = form(nameField, valueField);
        int result = JOptionPane.showConfirmDialog(this, form, "Edit Variable", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            v.setName(nameField.getText());
            v.setValue(valueField.getText());
            v.setUpdatedEpochMillis(System.currentTimeMillis());
            state.persistVariables();
            refresh();
        }
    }

    private void generateVariable() {
        GeneratorKind[] quick = {GeneratorKind.UUID, GeneratorKind.TIMESTAMP, GeneratorKind.HEX, GeneratorKind.BASE64,
                GeneratorKind.EMAIL, GeneratorKind.PHONE, GeneratorKind.RANDOM_INTEGER, GeneratorKind.RANDOM_STRING};
        GeneratorKind kind = (GeneratorKind) JOptionPane.showInputDialog(this, "Generator:", "Generate Value",
                JOptionPane.PLAIN_MESSAGE, null, quick, GeneratorKind.UUID);
        if (kind == null) return;
        List<String> values = state.generatorRegistry().generate(kind, new GeneratorParams().count(1));
        if (values.isEmpty()) return;
        JTextField nameField = new JTextField(20);
        JTextField valueField = new JTextField(values.get(0), 20);
        JPanel form = form(nameField, valueField);
        int result = JOptionPane.showConfirmDialog(this, form, "Save Generated Value As", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION && !nameField.getText().isBlank()) {
            state.variableStore().upsert(nameField.getText(), valueField.getText(), null);
            state.persistVariables();
            refresh();
        }
    }

    private void deleteSelected() {
        Variable v = selectedVariable();
        if (v == null) return;
        state.variableStore().remove(v.id());
        state.persistVariables();
        refresh();
    }

    private void trackValue() {
        Variable selected = selectedVariable();
        String initial = selected != null ? selected.value() : "";
        String value = JOptionPane.showInputDialog(this, "Value to search for across History, Intercept, and Payload Collections:", initial);
        if (value == null || value.isBlank()) {
            return;
        }
        List<ValueTracker.Occurrence> occurrences = ValueTracker.find(value, state);
        DefaultTableModel resultsModel = new DefaultTableModel(new Object[]{"Source", "Host", "Detail", "When"}, 0);
        for (ValueTracker.Occurrence o : occurrences) {
            resultsModel.addRow(new Object[]{o.source(), o.host(), o.detail(), timeFormat.format(new Date(o.whenEpochMillis()))});
        }
        JTable resultsTable = new JTable(resultsModel);
        JScrollPane scroll = new JScrollPane(resultsTable);
        scroll.setPreferredSize(new Dimension(700, 320));
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(new JLabel(occurrences.size() + " occurrence(s) of \"" + value + "\""), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, panel, "Track Value", JOptionPane.PLAIN_MESSAGE);
    }

    private static JPanel form(JTextField nameField, JTextField valueField) {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(labeled("Name:", nameField));
        form.add(labeled("Value:", valueField));
        return form;
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
