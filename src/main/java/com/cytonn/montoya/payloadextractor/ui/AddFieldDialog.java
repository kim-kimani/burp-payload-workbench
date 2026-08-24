package com.cytonn.montoya.payloadextractor.ui;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.cytonn.montoya.payloadextractor.modifier.RequestModifier;
import com.cytonn.montoya.payloadextractor.parser.FieldLocation;
import com.cytonn.montoya.payloadextractor.parser.MessageDirection;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Add / Duplicate a field. Before ever handing the new field back to the Workbench, this runs a
 * TRIAL {@code RequestModifier.buildComposedRequest} call against a copy of the working list - if
 * that throws (bad JSON parent path, whatever), the dialog shows the error and stays open instead
 * of creating a box that would always fail to compose. This is what makes Add/Duplicate
 * trustworthy: what you see previewed is guaranteed to actually compose.
 */
public final class AddFieldDialog {

    private AddFieldDialog() {
    }

    private static final FieldLocation[] ADDABLE_LOCATIONS = {
            FieldLocation.JSON_BODY, FieldLocation.COOKIE, FieldLocation.HEADER,
            FieldLocation.URL_PARAM, FieldLocation.FORM_PARAM, FieldLocation.MULTIPART_PARAM
    };

    public static Optional<ParsedField> showDialog(Component parent, HttpRequest originalRequest,
                                                     List<ParsedField> baselineFields, List<ParsedField> workingFields,
                                                     ParsedField prefillFrom) {
        JComboBox<FieldLocation> locationCombo = new JComboBox<>(ADDABLE_LOCATIONS);
        JTextField parentPathField = new JTextField(10);
        JTextField keyField = new JTextField(16);
        JTextField valueField = new JTextField(16);

        if (prefillFrom != null) {
            locationCombo.setSelectedItem(prefillFrom.location());
            if (prefillFrom.location() == FieldLocation.JSON_BODY) {
                String parentPath = com.cytonn.montoya.payloadextractor.util.JsonNode.parentPathOf(prefillFrom.path());
                parentPathField.setText(parentPath);
                keyField.setText(prefillFrom.rawKey() + "_copy");
            } else {
                keyField.setText(prefillFrom.rawKey() + "_copy");
            }
            valueField.setText(prefillFrom.currentValue());
        }

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(labeledRow("Location:", locationCombo));
        panel.add(labeledRow("JSON parent path (blank = root):", parentPathField));
        panel.add(labeledRow("Key / parameter / header name:", keyField));
        panel.add(labeledRow("Value:", valueField));

        while (true) {
            int choice = JOptionPane.showConfirmDialog(parent, panel,
                    prefillFrom != null ? "Duplicate Field" : "Add Field",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return Optional.empty();
            }
            FieldLocation location = (FieldLocation) locationCombo.getSelectedItem();
            String key = keyField.getText().trim();
            String value = valueField.getText();
            if (key.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "Key / parameter name cannot be empty.", "Invalid", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            String path = location == FieldLocation.JSON_BODY
                    ? (parentPathField.getText().trim().isEmpty() ? key : parentPathField.getText().trim() + "." + key)
                    : key;

            ParsedField candidate = ParsedField.builder()
                    .location(location)
                    .direction(MessageDirection.REQUEST)
                    .rawKey(key)
                    .path(path)
                    .headerName(location == FieldLocation.COOKIE ? "Cookie" : (location == FieldLocation.HEADER ? key : null))
                    .originalValue("")
                    .manuallyAdded(true)
                    .name(key)
                    .build();
            candidate.setCurrentValue(value);

            List<ParsedField> trial = new ArrayList<>(workingFields);
            trial.add(candidate);
            try {
                RequestModifier.buildComposedRequest(originalRequest, baselineFields, trial);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(parent, "This field can't be composed into the request: " + e.getMessage(),
                        "Invalid field", JOptionPane.ERROR_MESSAGE);
                continue;
            }
            return Optional.of(candidate);
        }
    }

    private static JPanel labeledRow(String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(6, 2));
        row.add(new JLabel(label), BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);
        row.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return row;
    }
}
