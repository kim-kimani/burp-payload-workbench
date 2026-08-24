package com.cytonn.montoya.payloadextractor.ui.panels;

import com.cytonn.montoya.payloadextractor.intercept.InterceptedMessage;
import com.cytonn.montoya.payloadextractor.util.ResponseSizeFormatter;

import javax.swing.table.AbstractTableModel;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing model for the Intercept tab's REQUEST HISTORY table: incremental insert/update (not a
 * full rebuild per event) so the table stays responsive under real traffic volume, with a
 * {@code javax.swing.RowSorter}/{@code RowFilter} layered on top by the panel for sorting and
 * search/filter without this model needing to know about either.
 */
public final class InterceptTableModel extends AbstractTableModel {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final String[] COLUMNS = {
            "ID", "Pin", "Host", "Path", "Method", "Status", "Response Size", "Time (ms)", "Timestamp", "State", "Tags", "Notes"
    };

    private final List<InterceptedMessage> rows = new ArrayList<>();

    public InterceptedMessage rowAt(int modelRow) {
        return rows.get(modelRow);
    }

    public void addRow(InterceptedMessage msg) {
        rows.add(msg);
        fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
    }

    public void updateRow(InterceptedMessage msg) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id() == msg.id()) {
                fireTableRowsUpdated(i, i);
                return;
            }
        }
    }

    public void clear(boolean keepPinned) {
        if (keepPinned) {
            rows.removeIf(m -> !m.isPinned());
        } else {
            rows.clear();
        }
        fireTableDataChanged();
    }

    public int indexOfId(int id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id() == id) return i;
        }
        return -1;
    }

    @Override
    public int getRowCount() { return rows.size(); }

    @Override
    public int getColumnCount() { return COLUMNS.length; }

    @Override
    public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 0, 5 -> Integer.class;
            case 1 -> Boolean.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        InterceptedMessage m = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> m.id();
            case 1 -> m.isPinned();
            case 2 -> m.host();
            case 3 -> m.path();
            case 4 -> m.method();
            case 5 -> m.statusCode();
            case 6 -> ResponseSizeFormatter.format(m.responseSizeBytes());
            case 7 -> m.roundTripMillis() < 0 ? "" : String.valueOf(m.roundTripMillis());
            case 8 -> FORMAT.format(Instant.ofEpochMilli(m.timestampEpochMillis()));
            case 9 -> m.holdPhase() != InterceptedMessage.HoldPhase.NONE ? "WAITING (" + m.holdPhase() + ")" : m.state().toString();
            case 10 -> String.join(", ", m.tags());
            case 11 -> m.notes();
            default -> "";
        };
    }
}
