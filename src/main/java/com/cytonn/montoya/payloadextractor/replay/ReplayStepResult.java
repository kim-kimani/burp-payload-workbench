package com.cytonn.montoya.payloadextractor.replay;

import burp.api.montoya.http.message.HttpRequestResponse;

/** The outcome of one replay step: a single request sent with one payload value substituted in. */
public final class ReplayStepResult {

    private final int stepIndex;
    private final String payloadValue;
    private final HttpRequestResponse requestResponse;
    private final Integer statusCode;
    private final long roundTripMillis;
    private final String error;

    public ReplayStepResult(int stepIndex, String payloadValue, HttpRequestResponse requestResponse,
                             Integer statusCode, long roundTripMillis, String error) {
        this.stepIndex = stepIndex;
        this.payloadValue = payloadValue;
        this.requestResponse = requestResponse;
        this.statusCode = statusCode;
        this.roundTripMillis = roundTripMillis;
        this.error = error;
    }

    public static ReplayStepResult success(int stepIndex, String payloadValue, HttpRequestResponse rr, long roundTripMillis) {
        Integer status = (rr != null && rr.hasResponse()) ? (int) rr.response().statusCode() : null;
        return new ReplayStepResult(stepIndex, payloadValue, rr, status, roundTripMillis, null);
    }

    /** Response size in bytes, or {@code null} if no response was received. */
    public Long responseSizeBytes() {
        return (requestResponse != null && requestResponse.hasResponse())
                ? (long) requestResponse.response().toByteArray().length() : null;
    }

    public static ReplayStepResult failure(int stepIndex, String payloadValue, String error) {
        return new ReplayStepResult(stepIndex, payloadValue, null, null, 0, error);
    }

    public int stepIndex() { return stepIndex; }
    public String payloadValue() { return payloadValue; }
    public HttpRequestResponse requestResponse() { return requestResponse; }
    public Integer statusCode() { return statusCode; }
    public long roundTripMillis() { return roundTripMillis; }
    public String error() { return error; }
    public boolean isError() { return error != null; }
}
