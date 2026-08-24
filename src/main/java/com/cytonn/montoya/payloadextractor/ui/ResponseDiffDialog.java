package com.cytonn.montoya.payloadextractor.ui;

import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.analysis.DiffResult;
import com.cytonn.montoya.payloadextractor.analysis.ResponseDiff;

import javax.swing.*;
import java.awt.*;

/**
 * "Compare responses" (item 7): two full responses side by side, reusing the same Montoya response
 * editor component (and its syntax highlighting) Workbench/Intercept/Replay already use, plus a
 * structured summary of what changed - status, size, headers added/removed/changed, JSON fields
 * added/removed/changed. The summary flags itself "worth a second look" when
 * {@link DiffResult#interesting} is set, but never claims a vulnerability - that call is left to
 * the analyst reading the actual bodies below.
 */
public final class ResponseDiffDialog extends JDialog {

    public static void showDialog(Component parent, ExtensionState state, String labelA, HttpResponse responseA,
                                   String labelB, HttpResponse responseB) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        ResponseDiffDialog dialog = new ResponseDiffDialog(owner, state, labelA, responseA, labelB, responseB);
        dialog.setVisible(true);
    }

    private ResponseDiffDialog(Window owner, ExtensionState state, String labelA, HttpResponse responseA,
                                String labelB, HttpResponse responseB) {
        super(owner, "Compare Responses", ModalityType.MODELESS);
        setLayout(new BorderLayout(8, 8));

        DiffResult diff = ResponseDiff.compare(responseA, responseB);

        JPanel summary = new JPanel();
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setBorder(BorderFactory.createTitledBorder("Summary"));

        JLabel headline = new JLabel((diff.interesting ? "⚑ " : "") + diff.summary());
        headline.setFont(headline.getFont().deriveFont(Font.BOLD));
        headline.setForeground(diff.interesting ? StyleKit.interestingColor() : StyleKit.neutral());
        summary.add(headline);

        if (diff.interesting) {
            JLabel caveat = new JLabel("Flagged as \"worth a second look\" only - not an automatic vulnerability claim.");
            caveat.setFont(caveat.getFont().deriveFont(Font.ITALIC, caveat.getFont().getSize2D() - 1f));
            caveat.setForeground(new Color(120, 120, 120));
            summary.add(caveat);
        }

        JTextArea detail = new JTextArea(buildDetailText(diff));
        detail.setEditable(false);
        detail.setLineWrap(false);
        JScrollPane detailScroll = new JScrollPane(detail);
        detailScroll.setPreferredSize(new Dimension(940, 140));
        summary.add(detailScroll);
        add(summary, BorderLayout.NORTH);

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder(labelA));
        HttpResponseEditor leftEditor = state.api().userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        leftEditor.setResponse(responseA != null ? responseA : HttpResponse.httpResponse());
        leftPanel.add(leftEditor.uiComponent(), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder(labelB));
        HttpResponseEditor rightEditor = state.api().userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        rightEditor.setResponse(responseB != null ? responseB : HttpResponse.httpResponse());
        rightPanel.add(rightEditor.uiComponent(), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setResizeWeight(0.5);
        add(split, BorderLayout.CENTER);

        setSize(1000, 700);
        setLocationRelativeTo(owner);
    }

    private static String buildDetailText(DiffResult diff) {
        StringBuilder sb = new StringBuilder();
        if (diff.statusChanged) {
            sb.append("Status: ").append(diff.oldStatus).append(" -> ").append(diff.newStatus).append('\n');
        }
        if (diff.sizeChangedSignificantly || (diff.oldSizeBytes != null && !diff.oldSizeBytes.equals(diff.newSizeBytes))) {
            sb.append("Size: ").append(diff.oldSizeBytes).append(" B -> ").append(diff.newSizeBytes).append(" B (")
              .append(diff.sizeDeltaBytes >= 0 ? "+" : "").append(diff.sizeDeltaBytes).append(" B)\n");
        }
        appendList(sb, "Headers added", diff.headersAdded);
        appendList(sb, "Headers removed", diff.headersRemoved);
        appendChanges(sb, "Headers changed", diff.headersChanged);
        appendChanges(sb, "JSON fields added", diff.jsonFieldsAdded);
        appendChanges(sb, "JSON fields removed", diff.jsonFieldsRemoved);
        appendChanges(sb, "JSON fields changed", diff.jsonFieldsChanged);
        if (diff.bodyDiffersNonJson) {
            sb.append("Body: differs (non-JSON body - see the two response panels below)\n");
        }
        if (sb.length() == 0) {
            sb.append("No differences found.");
        }
        return sb.toString();
    }

    private static void appendList(StringBuilder sb, String label, java.util.List<String> items) {
        if (items.isEmpty()) return;
        sb.append(label).append(": ").append(String.join(", ", items)).append('\n');
    }

    private static void appendChanges(StringBuilder sb, String label, java.util.List<DiffResult.FieldChange> changes) {
        if (changes.isEmpty()) return;
        sb.append(label).append(":\n");
        for (DiffResult.FieldChange c : changes) {
            sb.append("  ").append(c.path).append(": ").append(c.oldValue).append(" -> ").append(c.newValue).append('\n');
        }
    }
}
