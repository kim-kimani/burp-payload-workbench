package com.cytonn.montoya.payloadextractor.intercept;

import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import com.cytonn.montoya.payloadextractor.modifier.RuleEngine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A genuine, Montoya-native interception engine: implements {@link HttpHandler} and, when holding
 * is enabled, blocks Burp's own calling network thread inside {@code handleHttpRequestToBeSent}/
 * {@code handleHttpResponseReceived} until the analyst clicks Forward/Drop in the Intercept tab -
 * this is the same mechanism (a synchronous extension handler that doesn't return) every real
 * intercept-capable Burp extension uses; nothing here simulates UI clicks or reimplements HTTP.
 *
 * <p>Registered <em>after</em> {@code PassiveTrafficListener} in {@code PayloadExtractorExtension},
 * so payload learning always sees a request/response before this engine has a chance to hold or
 * drop it. Master-off (the default) makes every method here a pure pass-through - existing traffic
 * handling is completely unaffected unless the analyst explicitly turns Intercept on.
 */
public final class InterceptEngine implements HttpHandler {

    public interface Listener {
        void onMessageAdded(InterceptedMessage msg);
        void onMessageUpdated(InterceptedMessage msg);
    }

    private static final int MAX_HISTORY = 2000;

    private final RuleEngine ruleEngine = new RuleEngine();
    private final List<InterceptCondition> conditions = new CopyOnWriteArrayList<>();
    private final List<InterceptedMessage> history = new CopyOnWriteArrayList<>();
    private final Map<Integer, InterceptedMessage> byMessageId = new ConcurrentHashMap<>();
    private final Set<String> seenEndpoints = ConcurrentHashMap.newKeySet();
    private final Set<String> seenParams = ConcurrentHashMap.newKeySet();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final Logging logging;

    private volatile boolean masterOn = false;
    private volatile boolean interceptRequests = true;
    private volatile boolean interceptResponses = false;
    private volatile Listener listener;

    public InterceptEngine(Logging logging) {
        this.logging = logging;
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public RuleEngine ruleEngine() { return ruleEngine; }
    public List<InterceptCondition> conditions() { return conditions; }
    public List<InterceptedMessage> history() { return history; }

    public boolean isMasterOn() { return masterOn; }
    public void setMasterOn(boolean masterOn) {
        this.masterOn = masterOn;
        if (!masterOn) {
            forwardAllPending();
        }
    }
    public boolean isInterceptRequests() { return interceptRequests; }
    public void setInterceptRequests(boolean v) { this.interceptRequests = v; }
    public boolean isInterceptResponses() { return interceptResponses; }
    public void setInterceptResponses(boolean v) { this.interceptResponses = v; }

    public void clearHistory() {
        history.removeIf(m -> !m.isPinned());
    }

    /** Forwards every currently-held message as-is - used when the analyst flips master Intercept off, and as an explicit "Forward All" safety valve. */
    public void forwardAllPending() {
        for (InterceptedMessage m : history) {
            CompletableFuture<InterceptDecision> pending = m.pendingDecision();
            if (pending != null && !pending.isDone()) {
                if (m.holdPhase() == InterceptedMessage.HoldPhase.REQUEST) {
                    pending.complete(InterceptDecision.forwardRequest(m.currentRequest()));
                } else if (m.holdPhase() == InterceptedMessage.HoldPhase.RESPONSE) {
                    pending.complete(InterceptDecision.forwardResponse(m.currentResponse()));
                } else {
                    pending.complete(InterceptDecision.drop());
                }
            }
        }
    }

    // ---------------------------------------------------------------- HttpHandler

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        String host = requestToBeSent.httpService() != null ? requestToBeSent.httpService().host() : "";
        boolean isNewEndpoint = markEndpointSeen(requestToBeSent.method(), requestToBeSent.pathWithoutQuery());
        boolean isNewParameter = markParamsSeen(requestToBeSent);

        HttpRequest afterRules = safeApplyRequestRules(requestToBeSent, host);

        InterceptedMessage msg = new InterceptedMessage(idCounter.incrementAndGet(), afterRules, host);
        addToHistory(msg);
        byMessageId.put(requestToBeSent.messageId(), msg);

        boolean shouldHold = masterOn && interceptRequests && matchesAnyRequestCondition(afterRules, host, isNewEndpoint, isNewParameter);

        if (!shouldHold) {
            boolean changed = !afterRules.toString().equals(requestToBeSent.toString());
            msg.setState(changed ? InterceptState.EDITED_AND_FORWARDED : InterceptState.AUTO_FORWARDED);
            fireUpdated(msg);
            return RequestToBeSentAction.continueWith(afterRules);
        }

        msg.setHoldPhase(InterceptedMessage.HoldPhase.REQUEST);
        CompletableFuture<InterceptDecision> future = new CompletableFuture<>();
        msg.setPendingDecision(future);
        fireUpdated(msg);

        try {
            InterceptDecision decision = future.get();
            msg.setHoldPhase(InterceptedMessage.HoldPhase.NONE);
            if (decision.type() == InterceptDecision.Type.DROP) {
                msg.setState(InterceptState.DROPPED);
                fireUpdated(msg);
                return RequestToBeSentAction.drop();
            }
            HttpRequest finalRequest = decision.request() != null ? decision.request() : afterRules;
            boolean edited = !finalRequest.toString().equals(requestToBeSent.toString());
            msg.setCurrentRequest(finalRequest);
            msg.setState(edited ? InterceptState.EDITED_AND_FORWARDED : InterceptState.FORWARDED);
            fireUpdated(msg);
            return RequestToBeSentAction.continueWith(finalRequest);
        } catch (Exception e) {
            log("Intercept wait interrupted, forwarding as-is: " + e.getMessage());
            msg.setHoldPhase(InterceptedMessage.HoldPhase.NONE);
            msg.setState(InterceptState.AUTO_FORWARDED);
            fireUpdated(msg);
            return RequestToBeSentAction.continueWith(afterRules);
        }
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        HttpRequest initiating = responseReceived.initiatingRequest();
        String host = initiating != null && initiating.httpService() != null ? initiating.httpService().host() : "";
        String path = initiating != null ? initiating.pathWithoutQuery() : "";

        InterceptedMessage msg = byMessageId.remove(responseReceived.messageId());
        if (msg == null && initiating != null) {
            msg = new InterceptedMessage(idCounter.incrementAndGet(), initiating, host);
            addToHistory(msg);
        }
        if (msg != null) {
            msg.setRoundTripMillis(Math.max(0, System.currentTimeMillis() - msg.timestampEpochMillis()));
            msg.setOriginalResponse(responseReceived);
        }

        HttpResponse afterRules = safeApplyResponseRules(responseReceived, host, path);
        long sizeBytes = afterRules.toByteArray().length();

        boolean shouldHold = masterOn && interceptResponses && matchesAnyResponseCondition(afterRules, host, sizeBytes);

        if (!shouldHold) {
            if (msg != null) {
                msg.setCurrentResponse(afterRules);
                if (msg.state() != InterceptState.DROPPED) {
                    msg.setState(InterceptState.AUTO_FORWARDED);
                }
                fireUpdated(msg);
            }
            return ResponseReceivedAction.continueWith(afterRules);
        }

        InterceptedMessage finalMsg = msg;
        if (finalMsg != null) {
            finalMsg.setCurrentResponse(afterRules);
            finalMsg.setHoldPhase(InterceptedMessage.HoldPhase.RESPONSE);
        }
        CompletableFuture<InterceptDecision> future = new CompletableFuture<>();
        if (finalMsg != null) {
            finalMsg.setPendingDecision(future);
            fireUpdated(finalMsg);
        }

        try {
            InterceptDecision decision = future.get();
            if (finalMsg != null) {
                finalMsg.setHoldPhase(InterceptedMessage.HoldPhase.NONE);
            }
            HttpResponse finalResponse = decision.response() != null ? decision.response() : afterRules;
            if (finalMsg != null) {
                finalMsg.setCurrentResponse(finalResponse);
                finalMsg.setState(InterceptState.FORWARDED);
                fireUpdated(finalMsg);
            }
            return ResponseReceivedAction.continueWith(finalResponse);
        } catch (Exception e) {
            log("Intercept wait interrupted, forwarding response as-is: " + e.getMessage());
            if (finalMsg != null) {
                finalMsg.setHoldPhase(InterceptedMessage.HoldPhase.NONE);
                finalMsg.setState(InterceptState.AUTO_FORWARDED);
                fireUpdated(finalMsg);
            }
            return ResponseReceivedAction.continueWith(afterRules);
        }
    }

    // ---------------------------------------------------------------- helpers

    private HttpRequest safeApplyRequestRules(HttpRequest request, String host) {
        try {
            return ruleEngine.applyToRequest(request, host);
        } catch (Exception e) {
            log("Automatic Editor request rule failed, leaving request unmodified: " + e.getMessage());
            return request;
        }
    }

    private HttpResponse safeApplyResponseRules(HttpResponse response, String host, String path) {
        try {
            return ruleEngine.applyToResponse(response, host, path);
        } catch (Exception e) {
            log("Automatic Editor response rule failed, leaving response unmodified: " + e.getMessage());
            return response;
        }
    }

    private boolean matchesAnyRequestCondition(HttpRequest request, String host, boolean isNewEndpoint, boolean isNewParameter) {
        if (conditions.isEmpty()) {
            return true;
        }
        for (InterceptCondition c : conditions) {
            if (c.isEnabled() && c.matchesRequest(request, host, isNewEndpoint, isNewParameter)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesAnyResponseCondition(HttpResponse response, String host, long sizeBytes) {
        if (conditions.isEmpty()) {
            return true;
        }
        for (InterceptCondition c : conditions) {
            if (c.isEnabled() && c.matchesResponse(response, host, sizeBytes)) {
                return true;
            }
        }
        return false;
    }

    private boolean markEndpointSeen(String method, String path) {
        return seenEndpoints.add((method == null ? "" : method.toUpperCase()) + " " + path);
    }

    private boolean markParamsSeen(HttpRequest request) {
        boolean anyNew = false;
        try {
            for (var p : request.parameters()) {
                if (seenParams.add(p.type().name() + ":" + p.name())) {
                    anyNew = true;
                }
            }
        } catch (Exception ignored) {
        }
        return anyNew;
    }

    private void addToHistory(InterceptedMessage msg) {
        history.add(msg);
        if (history.size() > MAX_HISTORY) {
            for (InterceptedMessage m : history) {
                if (!m.isPinned()) {
                    history.remove(m);
                    break;
                }
            }
        }
        Listener l = listener;
        if (l != null) {
            javax.swing.SwingUtilities.invokeLater(() -> l.onMessageAdded(msg));
        }
    }

    private void fireUpdated(InterceptedMessage msg) {
        Listener l = listener;
        if (l != null) {
            javax.swing.SwingUtilities.invokeLater(() -> l.onMessageUpdated(msg));
        }
    }

    private void log(String message) {
        if (logging != null) {
            logging.logToError("[Intercept] " + message);
        }
    }
}
