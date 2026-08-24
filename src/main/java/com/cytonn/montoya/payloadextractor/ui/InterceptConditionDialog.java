package com.cytonn.montoya.payloadextractor.ui;

import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.intercept.InterceptCondition;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Manages the Intercept tab's condition list - both "conditional interception" (item 2) and
 * "Break On" presets (item 2's examples) are the same underlying mechanism here: every enabled
 * condition in the list is OR'd together, and each condition's own non-blank fields are AND'd. An
 * empty list means "intercept everything" (the master ON/OFF + Requests/Responses checkboxes still
 * gate whether anything is held at all).
 */
public final class InterceptConditionDialog extends JDialog {

    private final ExtensionState state;
    private final DefaultListModel<InterceptCondition> listModel = new DefaultListModel<>();
    private final JList<InterceptCondition> list = new JList<>(listModel);

    public static void showDialog(Component parent, ExtensionState state) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        InterceptConditionDialog dialog = new InterceptConditionDialog(owner, state);
        dialog.setVisible(true);
    }

    private InterceptConditionDialog(Window owner, ExtensionState state) {
        super(owner, "Break On / Conditional Interception", ModalityType.MODELESS);
        this.state = state;
        setLayout(new BorderLayout(8, 8));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel helper = new JLabel("<html>Empty list = intercept everything. Otherwise: hold if ANY enabled condition below matches "
                + "(each condition's own fields must ALL match).</html>");
        helper.setForeground(new Color(110, 110, 110));
        add(helper, BorderLayout.NORTH);

        refreshList();
        list.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
            JLabel l = new JLabel(value.label());
            l.setOpaque(true);
            l.setForeground(value.isEnabled() ? Color.BLACK : Color.GRAY);
            l.setBackground(isSelected ? new Color(220, 230, 245) : Color.WHITE);
            l.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return l;
        });
        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("Add Condition...");
        add.addActionListener(e -> editCondition(newCondition(), true));
        JButton edit = new JButton("Edit...");
        edit.addActionListener(e -> { if (list.getSelectedValue() != null) editCondition(list.getSelectedValue(), false); });
        JButton toggle = new JButton("Enable/Disable");
        toggle.addActionListener(e -> {
            InterceptCondition c = list.getSelectedValue();
            if (c != null) { c.setEnabled(!c.isEnabled()); refreshList(); persist(); }
        });
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> {
            InterceptCondition c = list.getSelectedValue();
            if (c != null) { state.interceptEngine().conditions().remove(c); refreshList(); persist(); }
        });
        buttons.add(add);
        buttons.add(edit);
        buttons.add(toggle);
        buttons.add(remove);
        add(buttons, BorderLayout.SOUTH);

        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT));
        presets.setBorder(BorderFactory.createTitledBorder("Quick add"));
        presets.add(presetButton("Status = 500", () -> withStatus("500")));
        presets.add(presetButton("Status = 403", () -> withStatus("403")));
        presets.add(presetButton("Response contains \"admin\"", () -> withResponseContains("admin")));
        presets.add(presetButton("Request contains \"userId\"", () -> withRequestContains("userId")));
        presets.add(presetButton("New endpoint", () -> { InterceptCondition c = newCondition(); c.setNewEndpointOnly(true); return c; }));
        presets.add(presetButton("New parameter", () -> { InterceptCondition c = newCondition(); c.setNewParameterOnly(true); return c; }));
        presets.add(presetButton("Response size > 100 KB", () -> { InterceptCondition c = newCondition(); c.setResponseSizeGreaterThanBytes(100_000L); return c; }));
        JPanel south = new JPanel(new BorderLayout());
        south.add(presets, BorderLayout.NORTH);
        add(south, BorderLayout.PAGE_END);

        setSize(560, 460);
        setLocationRelativeTo(owner);
    }

    private InterceptCondition newCondition() {
        return new InterceptCondition(null);
    }

    private InterceptCondition withStatus(String status) {
        InterceptCondition c = newCondition();
        c.setStatusCode(status);
        return c;
    }

    private InterceptCondition withResponseContains(String text) {
        InterceptCondition c = newCondition();
        c.setBodyContains(text);
        return c;
    }

    private InterceptCondition withRequestContains(String text) {
        InterceptCondition c = newCondition();
        c.setBodyContains(text);
        return c;
    }

    private JButton presetButton(String label, java.util.function.Supplier<InterceptCondition> factory) {
        JButton b = new JButton(label);
        b.addActionListener(e -> {
            InterceptCondition c = factory.get();
            List<InterceptCondition> conditions = state.interceptEngine().conditions();
            conditions.add(c);
            refreshList();
            persist();
        });
        return b;
    }

    private void refreshList() {
        InterceptCondition selected = list.getSelectedValue();
        listModel.clear();
        for (InterceptCondition c : state.interceptEngine().conditions()) {
            listModel.addElement(c);
        }
        if (selected != null) {
            list.setSelectedValue(selected, true);
        }
    }

    private void persist() {
        state.persistInterceptConfig();
    }

    private void editCondition(InterceptCondition c, boolean isNew) {
        if (isNew) {
            state.interceptEngine().conditions().add(c);
        }
        JTextField labelField = new JTextField(c.label(), 20);
        JTextField hostField = new JTextField(c.host(), 20);
        JTextField pathField = new JTextField(c.path(), 20);
        JTextField methodField = new JTextField(c.method(), 20);
        JTextField statusField = new JTextField(c.statusCode(), 20);
        JTextField headerNameField = new JTextField(c.headerName(), 20);
        JTextField headerValueField = new JTextField(c.headerValueContains(), 20);
        JTextField cookieField = new JTextField(c.cookieName(), 20);
        JTextField paramField = new JTextField(c.parameterName(), 20);
        JTextField bodyField = new JTextField(c.bodyContains(), 20);
        JCheckBox regexBox = new JCheckBox("Regex (path/body contain)", c.isRegex());
        JTextField sizeField = new JTextField(c.responseSizeGreaterThanBytes() == null ? "" : c.responseSizeGreaterThanBytes().toString(), 20);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(labeled("Label:", labelField));
        form.add(labeled("Host (glob):", hostField));
        form.add(labeled("Path (glob/contains):", pathField));
        form.add(labeled("Method:", methodField));
        form.add(labeled("Status code (500, >399, <300):", statusField));
        form.add(labeled("Header name:", headerNameField));
        form.add(labeled("Header value contains:", headerValueField));
        form.add(labeled("Cookie name:", cookieField));
        form.add(labeled("Parameter name:", paramField));
        form.add(labeled("Body contains:", bodyField));
        form.add(labeled("", regexBox));
        form.add(labeled("Response size > (bytes):", sizeField));

        int result = JOptionPane.showConfirmDialog(this, form, "Intercept Condition", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            c.setLabel(labelField.getText());
            c.setHost(hostField.getText());
            c.setPath(pathField.getText());
            c.setMethod(methodField.getText());
            c.setStatusCode(statusField.getText());
            c.setHeaderName(headerNameField.getText());
            c.setHeaderValueContains(headerValueField.getText());
            c.setCookieName(cookieField.getText());
            c.setParameterName(paramField.getText());
            c.setBodyContains(bodyField.getText());
            c.setRegex(regexBox.isSelected());
            try {
                c.setResponseSizeGreaterThanBytes(sizeField.getText().isBlank() ? null : Long.parseLong(sizeField.getText().trim()));
            } catch (NumberFormatException ignored) {
            }
            persist();
        } else if (isNew) {
            state.interceptEngine().conditions().remove(c);
        }
        refreshList();
    }

    private static JPanel labeled(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(480, 28));
        JLabel l = new JLabel(label);
        l.setPreferredSize(new Dimension(190, 20));
        p.add(l, BorderLayout.WEST);
        p.add(field, BorderLayout.CENTER);
        return p;
    }
}
