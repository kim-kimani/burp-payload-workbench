package com.cytonn.montoya.payloadextractor.ui;

import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.function.Function;

/**
 * Shared color/typography conventions so every table in the extension (Intercept, History, Replay)
 * speaks the same visual language instead of Replay inventing its own - item 8's "Do not create a
 * separate visual language". HTTP methods and status classes get the same colors everywhere they
 * appear, following the same "small colored badge/bold text" idiom {@code PayloadFieldComponent}
 * already uses for its field-location badges.
 */
public final class StyleKit {

    private StyleKit() {
    }

    public static Color methodColor(String method) {
        if (method == null) {
            return neutral();
        }
        switch (method.toUpperCase()) {
            case "GET": return new Color(60, 120, 200);
            case "POST": return new Color(60, 150, 90);
            case "PUT": return new Color(190, 130, 40);
            case "PATCH": return new Color(150, 110, 190);
            case "DELETE": return new Color(190, 60, 60);
            default: return neutral();
        }
    }

    public static Color statusColor(Integer status) {
        if (status == null) {
            return neutral();
        }
        int cls = status / 100;
        switch (cls) {
            case 2: return new Color(40, 140, 70);
            case 3: return new Color(60, 120, 200);
            case 4: return new Color(200, 130, 30);
            case 5: return new Color(190, 50, 50);
            default: return neutral();
        }
    }

    public static Color neutral() {
        return new Color(120, 120, 120);
    }

    /** Bold text color for the "flagged as interesting" marker used in comparison tables (see {@code ResponseDiff}). */
    public static Color interestingColor() {
        return new Color(190, 60, 60);
    }

    /** A bold, colored cell renderer - pass a function that derives the {@link Color} from the raw cell value (e.g. {@code StyleKit::methodColor} after a cast, or a lambda wrapping {@link #statusColor}). */
    public static DefaultTableCellRenderer coloredCellRenderer(Function<Object, Color> colorFn) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setForeground(colorFn.apply(value));
                }
                c.setFont(c.getFont().deriveFont(Font.BOLD));
                return c;
            }
        };
    }
}
