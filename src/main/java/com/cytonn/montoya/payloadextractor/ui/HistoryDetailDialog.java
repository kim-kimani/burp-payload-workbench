package com.cytonn.montoya.payloadextractor.ui;

import com.cytonn.montoya.payloadextractor.history.HistoryEntry;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Read-only detail view for a single {@link HistoryEntry}. */
public final class HistoryDetailDialog {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private HistoryDetailDialog() {
    }

    public static void showDialog(Component parent, HistoryEntry entry) {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        StringBuilder sb = new StringBuilder();
        sb.append("Time: ").append(FORMAT.format(Instant.ofEpochMilli(entry.timestampEpochMillis()))).append('\n');
        sb.append("Action: ").append(entry.action()).append('\n');
        sb.append("Field: ").append(entry.fieldName()).append('\n');
        sb.append("Location: ").append(entry.location()).append('\n');
        sb.append("Source: ").append(entry.source()).append('\n');
        if (entry.host() != null) {
            sb.append("Host: ").append(entry.host()).append('\n');
        }
        if (entry.statusCode() != null) {
            sb.append("Status: ").append(entry.statusCode()).append('\n');
        }
        if (entry.responseSizeBytes() != null) {
            sb.append("Response Size: ").append(com.cytonn.montoya.payloadextractor.util.ResponseSizeFormatter.format(entry.responseSizeBytes())).append('\n');
        }
        sb.append('\n');
        sb.append("Old value:\n").append(entry.oldValue()).append('\n');
        sb.append('\n');
        sb.append("New value:\n").append(entry.newValue()).append('\n');
        if (entry.notes() != null) {
            sb.append('\n').append("Notes: ").append(entry.notes());
        }
        area.setText(sb.toString());
        area.setCaretPosition(0);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(480, 320));
        JOptionPane.showMessageDialog(parent, scroll, "History Entry", JOptionPane.PLAIN_MESSAGE);
    }
}
