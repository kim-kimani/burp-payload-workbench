package com.cytonn.montoya.payloadextractor.generator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the v1.1.0 "OTP length" feature and the other generator kinds. */
class GeneratorTest {

    @Test
    void sequentialOtpStartsAtZeroAndIsZeroPadded() {
        GeneratorParams params = new GeneratorParams().count(5).length(6).min(0);
        List<String> values = new SequentialIntegerGenerator().generate(params);
        assertEquals("000000", values.get(0));
        assertEquals("000004", values.get(4));
        assertTrue(values.stream().allMatch(s -> s.length() == 6));
    }

    @Test
    void randomOtpRespectsFixedLength() {
        GeneratorParams params = new GeneratorParams().count(20).length(6);
        List<String> values = new RandomIntegerGenerator().generate(params);
        assertTrue(values.size() <= 20);
        assertTrue(values.stream().allMatch(s -> s.length() == 6));
    }

    @Test
    void nonUniqueFixedRangeReturnsRequestedCount() {
        GeneratorParams params = new GeneratorParams().count(3).min(5).max(5).unique(false);
        List<String> values = new RandomIntegerGenerator().generate(params);
        assertEquals(3, values.size());
        assertTrue(values.stream().allMatch(s -> s.equals("5")));
    }

    @Test
    void customPatternFillsTokensWithCorrectShape() {
        GeneratorParams params = new GeneratorParams().count(5).pattern("USER-{digit:4}-{alpha:2}");
        List<String> values = new CustomPatternGenerator().generate(params);
        assertTrue(values.stream().allMatch(s -> s.matches("USER-\\d{4}-[a-zA-Z]{2}")));
    }

    @Test
    void regexGeneratorProducesMatchingStringsWithRealPerRepetitionRandomness() {
        GeneratorParams params = new GeneratorParams().count(10).regex("[a-z]{5}[0-9]{3}");
        List<String> values = new RegexGenerator().generate(params);
        assertTrue(values.stream().allMatch(s -> s.matches("[a-z]{5}[0-9]{3}")));
        assertTrue(values.stream().distinct().count() > 1, "repeated character-class draws must be independently random");
    }

    @Test
    void customScriptGeneratorRunsMiniScriptFallback() {
        GeneratorParams params = new GeneratorParams().count(4)
                .customScript("otp = pad(randInt(0,999999), 6); \"OTP-\" + otp + \"-i\" + index");
        List<String> values = new CustomScriptGenerator().generate(params);
        assertTrue(values.get(0).matches("OTP-\\d{6}-i0"));
        assertTrue(values.get(3).endsWith("i3"));
    }

    @Test
    void wordlistGeneratorCyclesWhenCountExceedsListSize() {
        GeneratorParams params = new GeneratorParams().count(5).wordlistValues(List.of("a", "b", "c"));
        List<String> values = new WordlistGenerator().generate(params);
        assertEquals(List.of("a", "b", "c", "a", "b"), values);
    }
}
