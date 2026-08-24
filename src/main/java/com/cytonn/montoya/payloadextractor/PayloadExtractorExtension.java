package com.cytonn.montoya.payloadextractor;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import com.cytonn.montoya.payloadextractor.integration.ContextMenuProvider;
import com.cytonn.montoya.payloadextractor.integration.PassiveTrafficListener;
import com.cytonn.montoya.payloadextractor.ui.panels.MainPanel;

import javax.swing.SwingUtilities;

/**
 * Entry point: registers the suite tab, the right-click "Send to Payload Extractor" menu item,
 * and the passive traffic listener that feeds the payload collection database, then hands off to
 * {@link ExtensionState} as the single shared source of truth for everything else.
 */
public final class PayloadExtractorExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Montoya Payload Extractor");

        ExtensionState state = new ExtensionState(api);

        SwingUtilities.invokeLater(() -> {
            MainPanel mainPanel = new MainPanel(state);
            api.userInterface().registerSuiteTab("Payload Extractor", mainPanel);
        });

        api.userInterface().registerContextMenuItemsProvider(new ContextMenuProvider(state));
        // Registration order matters: passive learning always sees traffic first, so payload
        // collection keeps working exactly as before even while Intercept is holding/dropping
        // messages further down the handler chain.
        api.http().registerHttpHandler(new PassiveTrafficListener(state));
        api.http().registerHttpHandler(state.interceptEngine());

        api.extension().registerUnloadingHandler(state::persistAll);

        api.logging().logToOutput("Montoya Payload Extractor loaded - "
                + state.database().collectionCount() + " collection(s), "
                + state.database().valueCount() + " remembered value(s).");
    }
}
