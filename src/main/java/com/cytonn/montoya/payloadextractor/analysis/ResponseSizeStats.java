package com.cytonn.montoya.payloadextractor.analysis;

/**
 * A tiny running Average/Min/Max accumulator over response sizes (item 7's "response-size history
 * tracking") - fed one size at a time as results arrive (a replay run, a race-condition burst, a
 * multi-identity comparison), with no need to keep every sample around.
 */
public final class ResponseSizeStats {

    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max = Long.MIN_VALUE;

    public void reset() {
        count = 0;
        sum = 0;
        min = Long.MAX_VALUE;
        max = Long.MIN_VALUE;
    }

    public void add(long sizeBytes) {
        count++;
        sum += sizeBytes;
        if (sizeBytes < min) min = sizeBytes;
        if (sizeBytes > max) max = sizeBytes;
    }

    public long count() { return count; }
    public long min() { return count == 0 ? 0 : min; }
    public long max() { return count == 0 ? 0 : max; }
    public double average() { return count == 0 ? 0 : (double) sum / count; }

    public String summary() {
        if (count == 0) {
            return "No responses yet";
        }
        return "n=" + count
                + "  avg " + com.cytonn.montoya.payloadextractor.util.ResponseSizeFormatter.format(Math.round(average()))
                + "  min " + com.cytonn.montoya.payloadextractor.util.ResponseSizeFormatter.format(min())
                + "  max " + com.cytonn.montoya.payloadextractor.util.ResponseSizeFormatter.format(max());
    }
}
