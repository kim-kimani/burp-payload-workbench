package com.cytonn.montoya.payloadextractor.replay;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;

import java.util.List;

/**
 * Everything a {@link ReplayEngine} run needs: the already-composed base request (structure
 * settled via {@code RequestModifier.buildComposedRequest}, only the target field's value still
 * varies step to step), which field to vary, and the list of values to try it with - which may
 * come from a generator, a remembered payload collection, or a loaded wordlist file (all three
 * simply become a {@code List<String>} by the time it reaches here).
 */
public final class ReplayConfig {

    private final HttpRequest baseRequest;
    private final ParsedField targetField;
    private final List<String> payloadValues;
    private final ReplayOrder order;
    private final boolean parallel;
    private final int concurrency;
    private final Integer stopOnStatusCode;
    private final Integer maxRequests;
    private final long delayMillisBetweenRequests;

    private ReplayConfig(Builder b) {
        this.baseRequest = b.baseRequest;
        this.targetField = b.targetField;
        this.payloadValues = List.copyOf(b.payloadValues);
        this.order = b.order;
        this.parallel = b.parallel;
        this.concurrency = Math.max(1, b.concurrency);
        this.stopOnStatusCode = b.stopOnStatusCode;
        this.maxRequests = b.maxRequests;
        this.delayMillisBetweenRequests = b.delayMillisBetweenRequests;
    }

    public HttpRequest baseRequest() { return baseRequest; }
    public ParsedField targetField() { return targetField; }
    public List<String> payloadValues() { return payloadValues; }
    public ReplayOrder order() { return order; }
    public boolean isParallel() { return parallel; }
    public int concurrency() { return concurrency; }
    public Integer stopOnStatusCode() { return stopOnStatusCode; }
    public Integer maxRequests() { return maxRequests; }
    public long delayMillisBetweenRequests() { return delayMillisBetweenRequests; }

    public int effectiveStepCount() {
        int size = payloadValues.size();
        return maxRequests == null ? size : Math.min(size, maxRequests);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private HttpRequest baseRequest;
        private ParsedField targetField;
        private List<String> payloadValues = List.of();
        private ReplayOrder order = ReplayOrder.SEQUENTIAL;
        private boolean parallel = false;
        private int concurrency = 1;
        private Integer stopOnStatusCode;
        private Integer maxRequests;
        private long delayMillisBetweenRequests = 0;

        public Builder baseRequest(HttpRequest baseRequest) { this.baseRequest = baseRequest; return this; }
        public Builder targetField(ParsedField targetField) { this.targetField = targetField; return this; }
        public Builder payloadValues(List<String> payloadValues) { this.payloadValues = payloadValues; return this; }
        public Builder order(ReplayOrder order) { this.order = order; return this; }
        public Builder parallel(boolean parallel) { this.parallel = parallel; return this; }
        public Builder concurrency(int concurrency) { this.concurrency = concurrency; return this; }
        public Builder stopOnStatusCode(Integer stopOnStatusCode) { this.stopOnStatusCode = stopOnStatusCode; return this; }
        public Builder maxRequests(Integer maxRequests) { this.maxRequests = maxRequests; return this; }
        public Builder delayMillisBetweenRequests(long delay) { this.delayMillisBetweenRequests = delay; return this; }

        public ReplayConfig build() {
            if (baseRequest == null) throw new IllegalStateException("baseRequest is required");
            if (targetField == null) throw new IllegalStateException("targetField is required");
            return new ReplayConfig(this);
        }
    }
}
