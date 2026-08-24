package com.cytonn.montoya.payloadextractor.intercept;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * One row in the Intercept tab's REQUEST HISTORY: a request (and, once it arrives, its response),
 * held or already forwarded/dropped, with the analyst-facing metadata (pin/tags/notes) that survive
 * across the message's lifetime. While {@link #state} is {@code WAITING}, {@link #pendingDecision}
 * is the gate {@link InterceptEngine} blocks Burp's own network thread on until the analyst acts.
 */
public final class InterceptedMessage {

    public enum HoldPhase { REQUEST, RESPONSE, NONE }

    private final int id;
    private final long timestampEpochMillis;
    private volatile long roundTripMillis = -1;

    private volatile HttpRequest originalRequest;
    private volatile HttpRequest currentRequest;
    private volatile HttpResponse originalResponse;
    private volatile HttpResponse currentResponse;

    private final String host;
    private volatile InterceptState state = InterceptState.WAITING;
    private volatile HoldPhase holdPhase = HoldPhase.NONE;

    private volatile boolean pinned = false;
    private final Set<String> tags = new LinkedHashSet<>();
    private volatile String notes = "";

    private volatile transient CompletableFuture<InterceptDecision> pendingDecision;

    public InterceptedMessage(int id, HttpRequest request, String host) {
        this.id = id;
        this.timestampEpochMillis = System.currentTimeMillis();
        this.originalRequest = request;
        this.currentRequest = request;
        this.host = host;
    }

    public int id() { return id; }
    public long timestampEpochMillis() { return timestampEpochMillis; }
    public long roundTripMillis() { return roundTripMillis; }
    public void setRoundTripMillis(long v) { this.roundTripMillis = v; }

    public HttpRequest originalRequest() { return originalRequest; }
    public HttpRequest currentRequest() { return currentRequest; }
    public void setCurrentRequest(HttpRequest r) { this.currentRequest = r; }

    public HttpResponse originalResponse() { return originalResponse; }
    public void setOriginalResponse(HttpResponse r) { this.originalResponse = r; this.currentResponse = r; }
    public HttpResponse currentResponse() { return currentResponse; }
    public void setCurrentResponse(HttpResponse r) { this.currentResponse = r; }

    public String host() { return host; }
    public String method() { return currentRequest != null ? currentRequest.method() : ""; }
    public String path() { return currentRequest != null ? currentRequest.pathWithoutQuery() : ""; }

    public Integer statusCode() { return currentResponse != null ? (int) currentResponse.statusCode() : null; }
    public Long responseSizeBytes() { return currentResponse != null ? (long) currentResponse.toByteArray().length() : null; }

    public InterceptState state() { return state; }
    public void setState(InterceptState state) { this.state = state; }
    public HoldPhase holdPhase() { return holdPhase; }
    public void setHoldPhase(HoldPhase holdPhase) { this.holdPhase = holdPhase; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public Set<String> tags() { return tags; }
    public String notes() { return notes; }
    public void setNotes(String notes) { this.notes = notes == null ? "" : notes; }

    public CompletableFuture<InterceptDecision> pendingDecision() { return pendingDecision; }
    public void setPendingDecision(CompletableFuture<InterceptDecision> pendingDecision) { this.pendingDecision = pendingDecision; }
}
