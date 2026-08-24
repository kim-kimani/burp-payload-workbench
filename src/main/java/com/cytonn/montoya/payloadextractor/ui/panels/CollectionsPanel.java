package com.cytonn.montoya.payloadextractor.ui.panels;

import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.db.ImportExportManager;
import com.cytonn.montoya.payloadextractor.db.PayloadCollection;
import com.cytonn.montoya.payloadextractor.db.PayloadDatabase;
import com.cytonn.montoya.payloadextractor.db.PayloadSource;
import com.cytonn.montoya.payloadextractor.db.PayloadValue;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * "Payload Collections": browses the persistent {@link PayloadDatabase} - every remembered value,
 * grouped by normalized field name - with search, add/delete/favorite on individual values, and
 * whole-collection management (rename, merge, clear, deduplicate, import/export) plus whole-database
 * import/export/new. Rows whose origin host falls outside the top bar's "Show only in-scope items"
 * filter are hidden (view-only - nothing is deleted).
 */
public final class CollectionsPanel extends JPanel {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ExtensionState state;
    private final DefaultListModel<PayloadCollection> collectionListModel = new DefaultListModel<>();
    private final JList<PayloadCollection> collectionList = new JList<>(collectionListModel);
    private final JTextField searchField = new JTextField(16);
    private final DefaultTableModel valuesTableModel = new DefaultTableModel(new Object[]{"Active", "Value", "Source", "Host", "Favorite", "Created"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 0 ? Boolean.class : String.class;
        }
    };
    private final JTable valuesTable = new JTable(valuesTableModel);
    private List<PayloadValue> currentVisibleValues = List.of();

    public CollectionsPanel(ExtensionState state) {
        super(new BorderLayout(8, 8));
        this.state = state;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        add(buildLeft(), BorderLayout.WEST);
        add(buildRight(), BorderLayout.CENTER);
        add(buildBottomBar(), BorderLayout.SOUTH);

        valuesTableModel.addTableModelListener(e -> {
            if (e.getColumn() == 0) {
                PayloadCollection c = collectionList.getSelectedValue();
                int row = e.getFirstRow();
                if (c != null && row >= 0 && row < currentVisibleValues.size() && Boolean.TRUE.equals(valuesTableModel.getValueAt(row, 0))) {
                    c.setActive(currentVisibleValues.get(row).id());
                    persist();
                    refreshValuesTable();
                }
            }
        });

        refreshCollectionList();
    }

    // ---------------------------------------------------------------- left: collection list + management

    private JPanel buildLeft() {
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder("Collections"));
        left.setPreferredSize(new Dimension(260, 0));

        collectionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshValuesTable();
            }
        });
        left.add(new JScrollPane(collectionList), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(0, 1, 2, 2));
        buttons.add(actionButton("Rename...", e -> renameCollection()));
        buttons.add(actionButton("Merge Into...", e -> mergeCollection()));
        buttons.add(actionButton("Delete Collection", e -> deleteCollection()));
        buttons.add(actionButton("Clear Values", e -> clearValues()));
        buttons.add(actionButton("Deduplicate", e -> deduplicate()));
        buttons.add(actionButton("Import Values...", e -> importValues()));
        buttons.add(actionButton("Export Collection...", e -> exportCollection()));
        left.add(buttons, BorderLayout.SOUTH);
        return left;
    }

    // ---------------------------------------------------------------- right: search + table + value controls

    private JPanel buildRight() {
        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.setBorder(BorderFactory.createTitledBorder("Values"));

        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        searchBar.add(new JLabel("Search:"));
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshValuesTable(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshValuesTable(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshValuesTable(); }
        });
        searchBar.add(searchField);
        right.add(searchBar, BorderLayout.NORTH);

        right.add(new JScrollPane(valuesTable), BorderLayout.CENTER);

        JPanel valueControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField newValueField = new JTextField(20);
        JButton addValueButton = new JButton("Add Value");
        addValueButton.addActionListener(e -> {
            PayloadCollection c = collectionList.getSelectedValue();
            if (c != null && !newValueField.getText().isBlank()) {
                c.add(PayloadValue.of(newValueField.getText(), PayloadSource.MANUAL, System.currentTimeMillis(), null));
                persist();
                newValueField.setText("");
                refreshValuesTable();
            }
        });
        JButton removeValueButton = new JButton("Delete Selected");
        removeValueButton.addActionListener(e -> {
            PayloadCollection c = collectionList.getSelectedValue();
            int row = valuesTable.getSelectedRow();
            if (c != null && row >= 0 && row < currentVisibleValues.size()) {
                c.remove(currentVisibleValues.get(row).id());
                persist();
                refreshValuesTable();
            }
        });
        JButton favoriteButton = new JButton("Toggle Favorite");
        favoriteButton.addActionListener(e -> {
            int row = valuesTable.getSelectedRow();
            if (row >= 0 && row < currentVisibleValues.size()) {
                PayloadValue v = currentVisibleValues.get(row);
                v.setFavorite(!v.isFavorite());
                persist();
                refreshValuesTable();
            }
        });
        valueControls.add(new JLabel("New value:"));
        valueControls.add(newValueField);
        valueControls.add(addValueButton);
        valueControls.add(removeValueButton);
        valueControls.add(favoriteButton);
        right.add(valueControls, BorderLayout.SOUTH);
        return right;
    }

    private JPanel buildBottomBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(actionButton("New Collection...", e -> newCollection()));
        bar.add(actionButton("Import Whole Database...", e -> importDatabase()));
        bar.add(actionButton("Export Whole Database...", e -> exportDatabase()));
        return bar;
    }

    // ---------------------------------------------------------------- collection-list actions

    private void newCollection() {
        String name = JOptionPane.showInputDialog(this, "New collection name:", "New Collection", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.isBlank()) {
            state.database().findOrCreate(name.trim(), "GENERIC");
            persist();
            refreshCollectionList();
        }
    }

    private void renameCollection() {
        PayloadCollection c = collectionList.getSelectedValue();
        if (c == null) return;
        String name = JOptionPane.showInputDialog(this, "Rename collection to:", c.normalizedName());
        if (name != null && !name.isBlank()) {
            state.database().renameCollection(c.id(), name.trim());
            persist();
            refreshCollectionList();
        }
    }

    private void mergeCollection() {
        PayloadCollection source = collectionList.getSelectedValue();
        if (source == null) return;
        List<PayloadCollection> others = state.database().allCollections().stream()
                .filter(c -> !c.id().equals(source.id())).toList();
        if (others.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No other collection to merge into.", "Merge Into", JOptionPane.WARNING_MESSAGE);
            return;
        }
        PayloadCollection target = (PayloadCollection) JOptionPane.showInputDialog(this,
                "Merge \"" + source.normalizedName() + "\" into:", "Merge Into",
                JOptionPane.PLAIN_MESSAGE, null, others.toArray(), others.get(0));
        if (target != null) {
            state.database().mergeCollections(source.id(), target.id());
            persist();
            refreshCollectionList();
        }
    }

    private void deleteCollection() {
        PayloadCollection c = collectionList.getSelectedValue();
        if (c == null) return;
        int confirm = JOptionPane.showConfirmDialog(this, "Delete collection \"" + c.normalizedName() + "\" and all its values?",
                "Delete Collection", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            state.database().removeCollection(c.id());
            persist();
            refreshCollectionList();
        }
    }

    private void clearValues() {
        PayloadCollection c = collectionList.getSelectedValue();
        if (c == null) return;
        int confirm = JOptionPane.showConfirmDialog(this, "Remove every value from \"" + c.normalizedName() + "\"?",
                "Clear Values", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            c.values().clear();
            persist();
            refreshValuesTable();
        }
    }

    private void deduplicate() {
        PayloadCollection c = collectionList.getSelectedValue();
        if (c == null) return;
        int removed = c.deduplicate();
        persist();
        refreshValuesTable();
        JOptionPane.showMessageDialog(this, removed + " duplicate value(s) removed.", "Deduplicate", JOptionPane.INFORMATION_MESSAGE);
    }

    private void importValues() {
        PayloadCollection c = collectionList.getSelectedValue();
        if (c == null) {
            JOptionPane.showMessageDialog(this, "Select a collection first.", "Import Values", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import values (one per line) into \"" + c.normalizedName() + "\"");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                int added = ImportExportManager.importValuesIntoCollection(c, Path.of(chooser.getSelectedFile().getAbsolutePath()));
                persist();
                refreshValuesTable();
                JOptionPane.showMessageDialog(this, added + " value(s) imported.", "Done", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Import failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportCollection() {
        PayloadCollection c = collectionList.getSelectedValue();
        if (c == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export \"" + c.normalizedName() + "\" values");
        chooser.setSelectedFile(new File(c.normalizedName().replaceAll("\\W+", "_") + ".txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ImportExportManager.exportCollection(c, Path.of(chooser.getSelectedFile().getAbsolutePath()));
                JOptionPane.showMessageDialog(this, "Exported.", "Done", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importDatabase() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import / merge whole payload collection database (JSON)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                PayloadDatabase imported = ImportExportManager.importDatabase(Path.of(chooser.getSelectedFile().getAbsolutePath()));
                ImportExportManager.merge(state.database(), imported);
                persist();
                refreshCollectionList();
                JOptionPane.showMessageDialog(this, "Imported and merged.", "Done", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Import failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportDatabase() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export whole payload collection database to JSON");
        chooser.setSelectedFile(new File("payload-collections.json"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ImportExportManager.exportDatabase(state.database(), Path.of(chooser.getSelectedFile().getAbsolutePath()));
                JOptionPane.showMessageDialog(this, "Exported.", "Done", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ---------------------------------------------------------------- refresh

    public void refreshCollectionList() {
        PayloadCollection previouslySelected = collectionList.getSelectedValue();
        collectionListModel.clear();
        for (PayloadCollection c : state.database().allCollections()) {
            boolean anyVisible = c.isEmpty() || c.values().stream().anyMatch(v -> state.isHostVisible(v.originHost()));
            if (anyVisible) {
                collectionListModel.addElement(c);
            }
        }
        if (previouslySelected != null) {
            for (int i = 0; i < collectionListModel.size(); i++) {
                if (collectionListModel.get(i).id().equals(previouslySelected.id())) {
                    collectionList.setSelectedIndex(i);
                    break;
                }
            }
        }
        refreshValuesTable();
    }

    private void refreshValuesTable() {
        valuesTableModel.setRowCount(0);
        PayloadCollection c = collectionList.getSelectedValue();
        if (c == null) {
            currentVisibleValues = List.of();
            return;
        }
        String query = searchField.getText().trim().toLowerCase();
        String activeId = c.active().map(PayloadValue::id).orElse(null);
        currentVisibleValues = c.values().stream()
                .filter(v -> state.isHostVisible(v.originHost()))
                .filter(v -> query.isEmpty() || v.value().toLowerCase().contains(query))
                .toList();
        for (PayloadValue v : currentVisibleValues) {
            valuesTableModel.addRow(new Object[]{
                    v.id().equals(activeId),
                    v.value(),
                    v.source().displayName(),
                    v.originHost() == null ? "" : v.originHost(),
                    v.isFavorite() ? "★" : "",
                    v.capturedAtEpochMillis() > 0 ? FORMAT.format(Instant.ofEpochMilli(v.capturedAtEpochMillis())) : ""
            });
        }
    }

    private void persist() {
        state.persistenceManager().saveDatabase(state.database());
    }

    private static JButton actionButton(String text, java.awt.event.ActionListener listener) {
        JButton b = new JButton(text);
        b.addActionListener(listener);
        return b;
    }
}
