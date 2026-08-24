package com.cytonn.montoya.payloadextractor.integration;

import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import com.cytonn.montoya.payloadextractor.ExtensionState;
import com.cytonn.montoya.payloadextractor.db.PayloadSource;
import com.cytonn.montoya.payloadextractor.detector.PayloadDetector;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;

import java.util.List;

/**
 * Passively watches every request/response Burp's other tools send (Proxy, Repeater, Scanner,
 * ...), and - only for traffic inside the configured {@code ScopeFilter} - auto-detects and
 * remembers interesting field values into the {@link com.cytonn.montoya.payloadextractor.db.PayloadDatabase}.
 * This never modifies the traffic it observes: every handler method returns
 * {@code continueWith(...)} unchanged, so this listener is purely additive to the "Observe"
 * step of the Observe -> Extract -> Remember workflow.
 */
public final class PassiveTrafficListener implements HttpHandler {

    private final ExtensionState state;

    public PassiveTrafficListener(ExtensionState state) {
        this.state = state;
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        try {
            String host = requestToBeSent.httpService() != null ? requestToBeSent.httpService().host() : null;
            if (state.scopeFilter().isPassiveLearningEnabled() && state.scopeFilter().isInScope(host)) {
                long now = System.currentTimeMillis();
                List<ParsedField> fields = PayloadDetector.detectRequest(requestToBeSent);
                for (ParsedField f : PayloadDetector.onlyInteresting(fields)) {
                    if (!f.currentValue().isBlank()) {
                        state.database().remember(f.path(), f.category(), f.currentValue(), PayloadSource.OBSERVED, now, host);
                    }
                }
                state.persistenceManager().saveDatabase(state.database());
            }
        } catch (Exception e) {
            state.api().logging().logToError("Payload Extractor passive request scan failed: " + e.getMessage());
        }
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        try {
            String host = null;
            if (responseReceived.initiatingRequest() != null && responseReceived.initiatingRequest().httpService() != null) {
                host = responseReceived.initiatingRequest().httpService().host();
            }
            if (state.scopeFilter().isPassiveLearningEnabled() && state.scopeFilter().isInScope(host)) {
                long now = System.currentTimeMillis();
                List<ParsedField> fields = PayloadDetector.detectResponse(responseReceived);
                for (ParsedField f : PayloadDetector.onlyInteresting(fields)) {
                    if (!f.currentValue().isBlank()) {
                        state.database().remember(f.path(), f.category(), f.currentValue(), PayloadSource.OBSERVED, now, host);
                    }
                }
                state.persistenceManager().saveDatabase(state.database());
            }
        } catch (Exception e) {
            state.api().logging().logToError("Payload Extractor passive response scan failed: " + e.getMessage());
        }
        return ResponseReceivedAction.continueWith(responseReceived);
    }
}
