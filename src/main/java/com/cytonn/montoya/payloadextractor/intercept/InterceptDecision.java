package com.cytonn.montoya.payloadextractor.intercept;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

/** What the analyst chose to do with a held {@link InterceptedMessage} - the value {@code InterceptEngine}'s blocked network thread wakes up to. */
public final class InterceptDecision {

    public enum Type { FORWARD, DROP }

    private final Type type;
    private final HttpRequest request;
    private final HttpResponse response;

    private InterceptDecision(Type type, HttpRequest request, HttpResponse response) {
        this.type = type;
        this.request = request;
        this.response = response;
    }

    public static InterceptDecision forwardRequest(HttpRequest request) {
        return new InterceptDecision(Type.FORWARD, request, null);
    }

    public static InterceptDecision forwardResponse(HttpResponse response) {
        return new InterceptDecision(Type.FORWARD, null, response);
    }

    public static InterceptDecision drop() {
        return new InterceptDecision(Type.DROP, null, null);
    }

    public Type type() { return type; }
    public HttpRequest request() { return request; }
    public HttpResponse response() { return response; }
}
