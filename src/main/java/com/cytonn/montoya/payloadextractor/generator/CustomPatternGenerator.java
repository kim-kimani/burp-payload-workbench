package com.cytonn.montoya.payloadextractor.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills in a template pattern with random tokens, e.g. {@code USER-{digit:4}-{alpha:2}} might
 * produce {@code USER-8213-QK}. Supported tokens: {@code {digit}}, {@code {alpha}},
 * {@code {alnum}}, {@code {upper}}, {@code {lower}} - each optionally followed by
 * {@code :N} to repeat N times (default 1). Literal text outside {@code {...}} passes through
 * unchanged.
 */
public final class CustomPatternGenerator implements PayloadGenerator {

    private static final Pattern TOKEN = Pattern.compile("\\{(digit|alpha|alnum|upper|lower)(?::(\\d+))?}");

    private static final String DIGITS = "0123456789";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String ALPHA = LOWER + UPPER;
    private static final String ALNUM = ALPHA + DIGITS;

    @Override
    public GeneratorKind kind() {
        return GeneratorKind.CUSTOM_PATTERN;
    }

    @Override
    public List<String> generate(GeneratorParams params) {
        Random random = params.randomSeed() != null ? new Random(params.randomSeed()) : new Random();
        List<String> out = new ArrayList<>(Math.max(0, params.count()));
        for (int i = 0; i < params.count(); i++) {
            out.add(params.prefix() + fillOnce(params.pattern(), random) + params.suffix());
        }
        return out;
    }

    private static String fillOnce(String pattern, Random random) {
        if (pattern == null || pattern.isEmpty()) {
            return "";
        }
        Matcher m = TOKEN.matcher(pattern);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String charset = charsetFor(m.group(1));
            int repeat = m.group(2) != null ? Integer.parseInt(m.group(2)) : 1;
            StringBuilder rep = new StringBuilder(repeat);
            for (int i = 0; i < repeat; i++) {
                rep.append(charset.charAt(random.nextInt(charset.length())));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String charsetFor(String token) {
        switch (token) {
            case "digit": return DIGITS;
            case "alpha": return ALPHA;
            case "alnum": return ALNUM;
            case "upper": return UPPER;
            case "lower": return LOWER;
            default: return ALNUM;
        }
    }
}
