package com.cytonn.montoya.payloadextractor.integration;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import com.cytonn.montoya.payloadextractor.ExtensionState;

import javax.swing.JMenuItem;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adds a "Send to Payload Extractor" entry to Burp's right-click menu (Proxy history, Repeater,
 * Target site map, message editors, ...) so the analyst can open any request/response pair
 * straight into the Workbench without having to already be on this extension's tab.
 */
public final class ContextMenuProvider implements ContextMenuItemsProvider {

    private final ExtensionState state;

    public ContextMenuProvider(ExtensionState state) {
        this.state = state;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        Optional<HttpRequestResponse> target = resolveTarget(event);
        if (target.isEmpty()) {
            return List.of();
        }
        JMenuItem item = new JMenuItem("Send to Payload Extractor");
        item.addActionListener(e -> {
            if (state.mainPanel() != null) {
                state.mainPanel().openInWorkbench(target.get());
            }
        });
        List<Component> items = new ArrayList<>();
        items.add(item);
        return items;
    }

    private Optional<HttpRequestResponse> resolveTarget(ContextMenuEvent event) {
        if (!event.selectedRequestResponses().isEmpty()) {
            return Optional.of(event.selectedRequestResponses().get(0));
        }
        return event.messageEditorRequestResponse().map(m -> m.requestResponse());
    }
}
