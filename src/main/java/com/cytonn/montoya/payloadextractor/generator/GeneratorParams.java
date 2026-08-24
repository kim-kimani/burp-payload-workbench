package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameter bag covering every {@link GeneratorKind}'s options. Only the fields relevant to the
 * chosen kind are read; the rest are ignored. {@link #length} is the OTP-style "fixed digit
 * count, zero-padded, never truncated" option shared by the two integer generators - set it and
 * pick either sequential (starting wherever {@link #min} says, default 0) or random (drawn from
 * the full {@code [0, 10^length - 1]} range unless min/max are explicitly narrowed).
 */
public final class GeneratorParams {

    private int count = 10;
    private Integer length;
    private long min = 0;
    private long max = 999999;
    private boolean unique = true;
    private String charset = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private String prefix = "";
    private String suffix = "";
    private String pattern = "";
    private String regex = "";
    private List<String> wordlistValues = new ArrayList<>();
    private String customScript = "";
    private Long randomSeed;

    public int count() { return count; }
    public GeneratorParams count(int count) { this.count = count; return this; }

    public Integer length() { return length; }
    public GeneratorParams length(Integer length) { this.length = length; return this; }

    public long min() { return min; }
    public GeneratorParams min(long min) { this.min = min; return this; }

    public long max() { return max; }
    public GeneratorParams max(long max) { this.max = max; return this; }

    public boolean unique() { return unique; }
    public GeneratorParams unique(boolean unique) { this.unique = unique; return this; }

    public String charset() { return charset; }
    public GeneratorParams charset(String charset) { this.charset = (charset == null || charset.isEmpty()) ? this.charset : charset; return this; }

    public String prefix() { return prefix; }
    public GeneratorParams prefix(String prefix) { this.prefix = prefix == null ? "" : prefix; return this; }

    public String suffix() { return suffix; }
    public GeneratorParams suffix(String suffix) { this.suffix = suffix == null ? "" : suffix; return this; }

    public String pattern() { return pattern; }
    public GeneratorParams pattern(String pattern) { this.pattern = pattern == null ? "" : pattern; return this; }

    public String regex() { return regex; }
    public GeneratorParams regex(String regex) { this.regex = regex == null ? "" : regex; return this; }

    public List<String> wordlistValues() { return wordlistValues; }
    public GeneratorParams wordlistValues(List<String> values) { this.wordlistValues = values == null ? new ArrayList<>() : values; return this; }

    public String customScript() { return customScript; }
    public GeneratorParams customScript(String customScript) { this.customScript = customScript == null ? "" : customScript; return this; }

    public Long randomSeed() { return randomSeed; }
    public GeneratorParams randomSeed(Long randomSeed) { this.randomSeed = randomSeed; return this; }
}
