package com.cytonn.montoya.payloadextractor.ui;

import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.intercept.InterceptDirection;
import com.cytonn.montoya.payloadextractor.modifier.ModificationRule;
import com.cytonn.montoya.payloadextractor.modifier.RuleEngine;
import com.cytonn.montoya.payloadextractor.modifier.RuleLocation;

import javax.swing.*;
import java.awt.*;

/**
 * Manages the "Automatic Editor"'s ordered {@link ModificationRule} list: add/edit/remove/reorder/
 * enable-disable, each rule scoped by direction/location/host/path, with a live preview of what its
 * find/replace would do to a sample string. Changes take effect immediately (the engine reads the
 * live list) and are persisted via {@link ExtensionState#persistInterceptConfig()} on close.
 */
public final class ModificationRuleDialog extends JDialog {

    private final ExtensionState state;
    private final RuleEngine engine;
    private final DefaultListModel<ModificationRule> listModel = new DefaultListModel<>();
    private final JList<ModificationRule> list = new JList<>(listModel);

    public static void showDialog(Component parent, ExtensionState state) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        ModificationRuleDialog dialog = new ModificationRuleDialog(owner, state);
        dialog.setVisible(true);
    }

    /** "Create Rule from Selection": opens straight into a pre-filled new-rule editor for the given direction, seeded from selected editor text. */
    public static void showDialogForSelection(Component parent, ExtensionState state, String selectedText, InterceptDirection direction) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        ModificationRuleDialog dialog = new ModificationRuleDialog(owner, state);
        ModificationRule rule = dialog.engine.addRule();
        rule.setFind(selectedText);
        rule.setDirection(direction);
        rule.setLocation(direction == InterceptDirection.RESPONSE ? RuleLocation.RESPONSE_BODY : RuleLocation.ANYWHERE);
        dialog.refreshList();
        dialog.editRule(rule, true);
        dialog.setVisible(true);
    }

    private ModificationRuleDialog(Window owner, ExtensionState state) {
        super(owner, "Automatic Modification Engine - Rules", ModalityType.MODELESS);
        this.state = state;
        this.engine = state.interceptEngine().ruleEngine();
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JCheckBox masterEnabled = new JCheckBox("Automatic Editor enabled", engine.isEnabled());
        masterEnabled.addActionListener(e -> { engine.setEnabled(masterEnabled.isSelected()); persist(); });
        add(masterEnabled, BorderLayout.NORTH);

        refreshList();
        list.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
            JLabel l = new JLabel((index + 1) + ". " + value);
            l.setOpaque(true);
            l.setForeground(value.isEnabled() ? Color.BLACK : Color.GRAY);
            l.setBackground(isSelected ? new Color(220, 230, 245) : Color.WHITE);
            l.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return l;
        });
        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add Rule...");
        add.addActionListener(e -> editRule(engine.addRule(), true));
        JButton edit = new JButton("Edit...");
        edit.addActionListener(e -> { if (list.getSelectedValue() != null) editRule(list.getSelectedValue(), false); });
        JButton toggle = new JButton("Enable/Disable");
        toggle.addActionListener(e -> {
            ModificationRule r = list.getSelectedValue();
            if (r != null) { r.setEnabled(!r.isEnabled()); refreshList(); persist(); }
        });
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> {
            ModificationRule r = list.getSelectedValue();
            if (r != null) { engine.removeRule(r.id()); refreshList(); persist(); }
        });
        JButton up = new JButton("Move Up");
        up.addActionListener(e -> {
            ModificationRule r = list.getSelectedValue();
            if (r != null) { engine.moveUp(r.id()); refreshList(); list.setSelectedValue(r, true); persist(); }
        });
        JButton down = new JButton("Move Down");
        down.addActionListener(e -> {
            ModificationRule r = list.getSelectedValue();
            if (r != null) { engine.moveDown(r.id()); refreshList(); list.setSelectedValue(r, true); persist(); }
        });
        buttons.add(add);
        buttons.add(edit);
        buttons.add(toggle);
        buttons.add(remove);
        buttons.add(up);
        buttons.add(down);
        add(buttons, BorderLayout.SOUTH);

        setSize(560, 420);
        setLocationRelativeTo(owner);
    }

    private void refreshList() {
        ModificationRule selected = list.getSelectedValue();
        listModel.clear();
        for (ModificationRule r : engine.rules()) {
            listModel.addElement(r);
        }
        if (selected != null) {
            list.setSelectedValue(selected, true);
        }
    }

    private void persist() {
        state.persistInterceptConfig();
    }

    private void editRule(ModificationRule rule, boolean isNew) {
        JTextField nameField = new JTextField(rule.name(), 20);
        JComboBox<InterceptDirection> directionCombo = new JComboBox<>(InterceptDirection.values());
        directionCombo.setSelectedItem(rule.direction());
        JComboBox<RuleLocation> locationCombo = new JComboBox<>(RuleLocation.values());
        locationCombo.setSelectedItem(rule.location());
        JTextField findField = new JTextField(rule.find(), 20);
        JTextField replaceField = new JTextField(rule.replaceWith(), 20);
        JCheckBox regexBox = new JCheckBox("Regex", rule.isRegex());
        JTextField hostScopeField = new JTextField(rule.hostScope(), 20);
        JTextField pathScopeField = new JTextField(rule.pathScope(), 20);
        JTextField previewInput = new JTextField("/api/users/555", 20);
        JLabel previewOutput = new JLabel(" ");
        previewOutput.setForeground(new Color(30, 130, 30));

        Runnable updatePreview = () -> {
            ModificationRule scratch = new ModificationRule(null);
            scratch.setFind(findField.getText());
            scratch.setReplaceWith(replaceField.getText());
            scratch.setRegex(regexBox.isSelected());
            previewOutput.setText(previewInput.getText() + "  ->  " + scratch.preview(previewInput.getText()));
        };
        for (JTextField f : new JTextField[]{findField, replaceField, previewInput}) {
            f.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreview.run(); }
            });
        }
        regexBox.addActionListener(e -> updatePreview.run());
        updatePreview.run();

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(labeled("Name:", nameField));
        form.add(labeled("Direction:", directionCombo));
        form.add(labeled("Location:", locationCombo));
        form.add(labeled("Find:", findField));
        form.add(labeled("Replace with:", replaceField));
        form.add(labeled("", regexBox));
        form.add(labeled("Host scope (glob, blank = any):", hostScopeField));
        form.add(labeled("Path scope (glob, blank = any):", pathScopeField));
        form.add(new JSeparator());
        form.add(labeled("Preview on:", previewInput));
        form.add(previewOutput);

        int result = JOptionPane.showConfirmDialog(this, form, "Modification Rule", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            rule.setName(nameField.getText());
            rule.setDirection((InterceptDirection) directionCombo.getSelectedItem());
            rule.setLocation((RuleLocation) locationCombo.getSelectedItem());
            rule.setFind(findField.getText());
            rule.setReplaceWith(replaceField.getText());
            rule.setRegex(regexBox.isSelected());
            rule.setHostScope(hostScopeField.getText());
            rule.setPathScope(pathScopeField.getText());
            persist();
        } else if (isNew) {
            engine.removeRule(rule.id());
        }
        refreshList();
    }

    private static JPanel labeled(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(480, 28));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(170, 20));
        p.add(l, BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }
}
