package com.cytonn.montoya.payloadextractor.ui.panels;

import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.history.HistoryEntry;
import com.cytonn.montoya.payloadextractor.ui.HistoryDetailDialog;
import com.cytonn.montoya.payloadextractor.util.ResponseSizeFormatter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Read-only audit log of every substitution/add/remove/reorder/replay/send action taken in the
 * Workbench: #, Time, Host, Parameter, Original, Test Value, Status. Rows whose host falls outside
 * the top bar's "Show only in-scope items" filter are hidden (view-only - nothing is deleted).
 * Double-click a row for full before/after detail.
 */
public final class HistoryPanel extends JPanel {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ExtensionState state;
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"#", "Time", "Host", "Parameter", "Original", "Test Value", "Status", "Response Size"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private List<HistoryEntry> currentRows = List.of();

    public HistoryPanel(ExtensionState state) {
        super(new BorderLayout(8, 8));
        this.state = state;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JTable table = new JTable(model);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0 && row < currentRows.size()) {
                        HistoryDetailDialog.showDialog(HistoryPanel.this, currentRows.get(row));
                    }
                }
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refresh());
        JButton clearButton = new JButton("Clear History");
        clearButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Clear the entire history log?", "Clear History", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                state.historyManager().clear();
                refresh();
            }
        });
        JButton exportButton = new JButton("Export History");
        exportButton.addActionListener(e -> exportHistory());
        toolbar.add(refreshButton);
        toolbar.add(clearButton);
        toolbar.add(exportButton);
        add(toolbar, BorderLayout.NORTH);

        refresh();
    }

    public void refresh() {
        currentRows = state.historyManager().all().stream()
                .filter(h -> state.isHostVisible(h.host()))
                .toList();
        model.setRowCount(0);
        int i = 1;
        for (HistoryEntry e : currentRows) {
            model.addRow(new Object[]{
                    i++,
                    FORMAT.format(Instant.ofEpochMilli(e.timestampEpochMillis())),
                    e.host() == null ? "" : e.host(),
                    e.fieldName(),
                    truncate(e.oldValue()),
                    truncate(e.newValue()),
                    e.statusCode() == null ? "" : e.statusCode().toString(),
                    ResponseSizeFormatter.format(e.responseSizeBytes())
            });
        }
    }

    private void exportHistory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export history log (CSV)");
        chooser.setSelectedFile(new File("payload-extractor-history.csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                StringBuilder sb = new StringBuilder("#,Time,Host,Parameter,Original,Test Value,Status,Response Size\n");
                int i = 1;
                for (HistoryEntry e : currentRows) {
                    sb.append(i++).append(',')
                      .append(csv(FORMAT.format(Instant.ofEpochMilli(e.timestampEpochMillis())))).append(',')
                      .append(csv(e.host())).append(',')
                      .append(csv(e.fieldName())).append(',')
                      .append(csv(e.oldValue())).append(',')
                      .append(csv(e.newValue())).append(',')
                      .append(csv(e.statusCode() == null ? "" : e.statusCode().toString())).append(',')
                      .append(csv(ResponseSizeFormatter.format(e.responseSizeBytes())))
                      .append('\n');
                }
                Files.writeString(Path.of(chooser.getSelectedFile().getAbsolutePath()), sb.toString(), StandardCharsets.UTF_8);
                JOptionPane.showMessageDialog(this, "Exported.", "Done", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static String csv(String s) {
        if (s == null) return "";
        String escaped = s.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
