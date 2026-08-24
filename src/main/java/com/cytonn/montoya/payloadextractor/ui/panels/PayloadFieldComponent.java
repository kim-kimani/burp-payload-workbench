package com.cytonn.montoya.payloadextractor.ui.panels;

import com.cytonn.montoya.payloadextractor.db.PayloadCollection;
import com.cytonn.montoya.payloadextractor.db.PayloadValue;
import com.cytonn.montoya.payloadextractor.detector.PayloadCategory;
import com.cytonn.montoya.payloadextractor.parser.FieldLocation;
import com.cytonn.montoya.payloadextractor.parser.MessageDirection;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.List;

/**
 * One compact, single-line row in the Workbench's full-width "Detected Payloads" list, representing
 * a single {@link ParsedField}: a location badge, a location label, the bold field name, an
 * editable dropdown pre-populated from that field's remembered {@link PayloadCollection} (pick an
 * old value like a country-code picker, or type a new one), a favorite star, Gen/Play/Dup buttons,
 * ▲▼ reorder buttons, and the remove ✕. Every action here reports back through {@link WorkbenchHooks}
 * to {@link WorkbenchPanel}, which is the only place that actually rebuilds the composed request;
 * this component never touches the request itself.
 */
public final class PayloadFieldComponent extends JPanel {

    private static final int ROW_HEIGHT = 30;

    private final ParsedField field;
    private final WorkbenchHooks hooks;
    private final JComboBox<String> valueCombo;
    private final JTextField valueEditor;
    private final JCheckBox playToggle;
    private final JLabel gripLabel;
    private final JButton favoriteButton;

    public PayloadFieldComponent(ParsedField field, WorkbenchHooks hooks) {
        super(new BorderLayout(4, 0));
        this.field = field;
        this.hooks = hooks;

        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(225, 225, 225)),
                BorderFactory.createEmptyBorder(2, 4, 2, 4)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_HEIGHT));
        setPreferredSize(new Dimension(760, ROW_HEIGHT));

        // ---- left cluster: drag grip + location badge + location label + bold name
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));

        boolean reorderable = field.location().supportsRealReorder();
        gripLabel = new JLabel("≡"); // ≡
        gripLabel.setToolTipText(reorderable
                ? "Drag to reorder - this changes the real field order in the composed request"
                : "Drag-reorder isn't available for " + field.location().displayName()
                        + " (the Montoya API has no wire-order-control for this field type); add/remove still apply for real");
        gripLabel.setForeground(reorderable ? Color.DARK_GRAY : new Color(205, 205, 205));
        gripLabel.setCursor(Cursor.getPredefinedCursor(reorderable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        gripLabel.setPreferredSize(new Dimension(14, ROW_HEIGHT));
        if (reorderable) {
            installDragHandlers();
        }
        left.add(gripLabel);
        left.add(Box.createHorizontalStrut(4));

        JLabel badge = new JLabel(locationIcon(field.location()), javax.swing.SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setBackground(locationColor(field.location()));
        badge.setForeground(Color.WHITE);
        badge.setFont(badge.getFont().deriveFont(Font.BOLD, 10f));
        badge.setPreferredSize(new Dimension(24, 18));
        badge.setMaximumSize(new Dimension(24, 18));
        badge.setToolTipText(locationLabel());
        left.add(badge);
        left.add(Box.createHorizontalStrut(6));

        JLabel locLabel = new JLabel(locationLabel());
        locLabel.setForeground(new Color(120, 120, 120));
        locLabel.setFont(locLabel.getFont().deriveFont(11f));
        locLabel.setPreferredSize(new Dimension(120, ROW_HEIGHT));
        left.add(locLabel);
        left.add(Box.createHorizontalStrut(6));

        JLabel nameLabel = new JLabel(shortLabel());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        nameLabel.setToolTipText(field.name() + "  [" + field.path() + "]" + (field.manuallyAdded() ? "  (added)" : ""));
        nameLabel.setPreferredSize(new Dimension(160, ROW_HEIGHT));
        left.add(nameLabel);

        add(left, BorderLayout.WEST);

        // ---- center: editable "country-picker" style value dropdown, backed by this field's collection
        valueCombo = new JComboBox<>();
        valueCombo.setEditable(true);
        valueCombo.setModel(new DefaultComboBoxModel<>(collectionValuesArray()));
        valueCombo.setSelectedItem(field.currentValue());
        valueCombo.setToolTipText("Type a new value, or pick a remembered one for this field");
        valueEditor = (JTextField) valueCombo.getEditor().getEditorComponent();
        valueEditor.setText(field.currentValue());
        valueEditor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { pushValue(); }
            public void removeUpdate(DocumentEvent e) { pushValue(); }
            public void changedUpdate(DocumentEvent e) { pushValue(); }

            private void pushValue() {
                field.setCurrentValue(valueEditor.getText());
                hooks.onValueChanged(field, valueEditor.getText());
            }
        });
        add(valueCombo, BorderLayout.CENTER);

        // ---- right cluster: favorite, Gen/Play/Dup, move up/down, remove
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));

        favoriteButton = smallButton(favoriteGlyph(), "Toggle favorite for this field's current value", e -> {
            hooks.onFavoriteToggled(field);
            refreshFavoriteGlyph();
        });
        controls.add(favoriteButton);

        playToggle = new JCheckBox("Play", field.isEnabled());
        playToggle.setOpaque(false);
        playToggle.setToolTipText("When off, this field's original value is kept even if edited/generated/replayed");
        playToggle.addActionListener(e -> {
            field.setEnabled(playToggle.isSelected());
            hooks.onPlayToggled(field, playToggle.isSelected());
        });
        controls.add(playToggle);

        controls.add(smallButton("Type", "Manually tell the extension what kind of value this field holds (affects generator defaults and grouping)", e -> showCategoryMenu()));
        controls.add(smallButton("Gen", "Generate payload values for this field", e -> hooks.onGenerateRequested(field)));
        controls.add(smallButton("Play▶", "Replay this field with a generated/remembered/file value list", e -> hooks.onReplayRequested(field)));
        controls.add(smallButton("Dup", "Duplicate this field", e -> hooks.onDuplicateRequested(field)));

        JButton upButton = smallButton("▲", "Move up within its group", e -> hooks.onMoveUpRequested(field));
        JButton downButton = smallButton("▼", "Move down within its group", e -> hooks.onMoveDownRequested(field));
        if (!reorderable) {
            upButton.setEnabled(false);
            downButton.setEnabled(false);
            String tip = "Reorder isn't available for " + field.location().displayName() + " (Montoya API limitation)";
            upButton.setToolTipText(tip);
            downButton.setToolTipText(tip);
        }
        controls.add(upButton);
        controls.add(downButton);

        JButton removeButton = smallButton("✕", "Remove this field - also strips it from the real request body/header/cookie", e -> hooks.onRemoveRequested(field));
        removeButton.setForeground(new Color(170, 30, 30));
        controls.add(removeButton);

        add(controls, BorderLayout.EAST);
    }

    private String locationLabel() {
        String base = field.location().displayName();
        return field.direction() == MessageDirection.RESPONSE ? base + " (response)" : base;
    }

    private static String locationIcon(FieldLocation loc) {
        switch (loc) {
            case JSON_BODY: return "{}";
            case XML_BODY: return "<>";
            case COOKIE: return "C";
            case HEADER: return "H";
            case URL_PARAM: return "?";
            case FORM_PARAM: return "F";
            case MULTIPART_PARAM: return "M";
            default: return "R";
        }
    }

    private static Color locationColor(FieldLocation loc) {
        switch (loc) {
            case JSON_BODY: return new Color(60, 120, 200);
            case XML_BODY: return new Color(120, 90, 190);
            case COOKIE: return new Color(190, 130, 40);
            case HEADER: return new Color(90, 150, 90);
            case URL_PARAM: return new Color(150, 90, 150);
            case FORM_PARAM: return new Color(80, 150, 160);
            case MULTIPART_PARAM: return new Color(150, 110, 80);
            default: return new Color(130, 130, 130);
        }
    }

    private String[] collectionValuesArray() {
        PayloadCollection collection = hooks.collectionFor(field);
        List<PayloadValue> values = collection.values();
        return values.stream()
                .sorted(Comparator.comparing(PayloadValue::isFavorite).reversed()
                        .thenComparing(Comparator.comparingLong(PayloadValue::capturedAtEpochMillis).reversed()))
                .map(PayloadValue::value)
                .distinct()
                .toArray(String[]::new);
    }

    private String favoriteGlyph() {
        return hooks.isFavorite(field) ? "★" : "☆"; // ★ / ☆
    }

    private void refreshFavoriteGlyph() {
        favoriteButton.setText(favoriteGlyph());
    }

    private void showCategoryMenu() {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        for (PayloadCategory cat : PayloadCategory.values()) {
            javax.swing.JMenuItem item = new javax.swing.JMenuItem(cat.displayName()
                    + (cat.name().equals(field.category()) ? "  (current)" : ""));
            item.addActionListener(ev -> hooks.onCategoryReassigned(field, cat.name()));
            menu.add(item);
        }
        menu.show(this, 0, getHeight());
    }

    private String shortLabel() {
        String base = field.rawKey();
        return base.length() > 22 ? base.substring(0, 20) + "…" : base;
    }

    private static JButton smallButton(String text, String tooltip, java.awt.event.ActionListener listener) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setMargin(new java.awt.Insets(1, 4, 1, 4));
        b.addActionListener(listener);
        return b;
    }

    public ParsedField field() {
        return field;
    }

    /** Refreshes the value editor text from the field's current value without re-triggering onValueChanged (e.g. after a generator/replay/AI-suggestion write). */
    public void refreshValueDisplay() {
        if (!valueEditor.getText().equals(field.currentValue())) {
            valueEditor.setText(field.currentValue());
        }
        refreshFavoriteGlyph();
    }

    // ---------------------------------------------------------------- drag-to-reorder

    private void installDragHandlers() {
        MouseAdapter dragHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(70, 120, 200)),
                        BorderFactory.createEmptyBorder(2, 4, 2, 4)));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(225, 225, 225)),
                        BorderFactory.createEmptyBorder(2, 4, 2, 4)));
                JComponent parent = (JComponent) getParent();
                if (parent == null) {
                    return;
                }
                Point releaseInParent = SwingUtilities.convertPoint(gripLabel, e.getPoint(), parent);
                int targetIndex = indexForY(parent, releaseInParent.y);
                hooks.onReorderRequested(field, targetIndex);
            }
        };
        gripLabel.addMouseListener(dragHandler);
    }

    /** Finds which child slot a given Y coordinate (in the parent's coordinate space) falls into, for a top-to-bottom BoxLayout list. */
    private static int indexForY(JComponent parent, int y) {
        int count = parent.getComponentCount();
        for (int i = 0; i < count; i++) {
            java.awt.Component child = parent.getComponent(i);
            java.awt.Rectangle bounds = child.getBounds();
            if (y < bounds.y + bounds.height / 2) {
                return i;
            }
        }
        return count - 1;
    }
}
