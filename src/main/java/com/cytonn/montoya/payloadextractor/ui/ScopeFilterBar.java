package com.cytonn.montoya.payloadextractor.ui;

import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.db.ScopeFilter;

import javax.swing.*;
import java.awt.*;

/** Small toolbar for controlling which hosts the passive listener/auto-detect pipeline pays attention to. */
public final class ScopeFilterBar extends JPanel {

    private final ExtensionState state;
    private final DefaultListModel<String> includeModel = new DefaultListModel<>();
    private final DefaultListModel<String> excludeModel = new DefaultListModel<>();

    public ScopeFilterBar(ExtensionState state) {
        super(new BorderLayout(6, 6));
        this.state = state;
        ScopeFilter filter = state.scopeFilter();

        JCheckBox enabledBox = new JCheckBox("Restrict to scope", filter.isEnabled());
        enabledBox.addActionListener(e -> {
            filter.setEnabled(enabledBox.isSelected());
            persist();
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(enabledBox);
        add(top, BorderLayout.NORTH);

        JPanel lists = new JPanel(new GridLayout(1, 2, 8, 0));
        lists.add(buildPatternPanel("Include host patterns (e.g. *.example.com)", includeModel, filter.includeHostPatterns(), true));
        lists.add(buildPatternPanel("Exclude host patterns", excludeModel, filter.excludeHostPatterns(), false));
        add(lists, BorderLayout.CENTER);
    }

    private JPanel buildPatternPanel(String title, DefaultListModel<String> model, java.util.List<String> initial, boolean include) {
        for (String p : initial) {
            model.addElement(p);
        }
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        JList<String> list = new JList<>(model);
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(4, 0));
        JTextField input = new JTextField();
        JButton add = new JButton("Add");
        add.addActionListener(e -> {
            String pattern = input.getText().trim();
            if (!pattern.isEmpty()) {
                model.addElement(pattern);
                if (include) {
                    state.scopeFilter().addInclude(pattern);
                } else {
                    state.scopeFilter().addExclude(pattern);
                }
                persist();
                input.setText("");
            }
        });
        JButton remove = new JButton("Remove Selected");
        remove.addActionListener(e -> {
            String selected = list.getSelectedValue();
            if (selected != null) {
                model.removeElement(selected);
                if (include) {
                    state.scopeFilter().removeInclude(selected);
                } else {
                    state.scopeFilter().removeExclude(selected);
                }
                persist();
            }
        });
        controls.add(input, BorderLayout.CENTER);
        controls.add(add, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.add(controls, BorderLayout.NORTH);
        south.add(remove, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void persist() {
        state.persistenceManager().saveScopeFilter(state.scopeFilter());
    }
}
