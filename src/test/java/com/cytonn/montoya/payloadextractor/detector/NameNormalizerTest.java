package com.cytonn.montoya.payloadextractor.detector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameNormalizerTest {

    @Test
    void camelCaseAndSnakeCaseAndKebabCaseNormalizeToTheSameForm() {
        String expected = NameNormalizer.normalForm("authToken");
        assertEquals(expected, NameNormalizer.normalForm("auth_token"));
        assertEquals(expected, NameNormalizer.normalForm("auth-token"));
        assertEquals(expected, NameNormalizer.normalForm("AUTH_TOKEN"));
    }

    @Test
    void dottedJsonPathUsesLastSegmentOnly() {
        assertEquals("otp code", NameNormalizer.normalForm("user.otpCode"));
        assertEquals("value", NameNormalizer.normalForm("nested.roles[0].value"));
    }

    @Test
    void displayNameIsTitleCase() {
        assertEquals("Otp Code", NameNormalizer.displayName("user.otpCode"));
        assertEquals("Auth Token", NameNormalizer.displayName("auth_token"));
    }

    @Test
    void interestingKeyMatcherCategorizesCommonNames() {
        assertEquals(PayloadCategory.OTP, InterestingKeyMatcher.categorize("otpCode"));
        assertEquals(PayloadCategory.AUTH_TOKEN, InterestingKeyMatcher.categorize("Authorization"));
        assertEquals(PayloadCategory.EMAIL, InterestingKeyMatcher.categorize("user_email"));
        assertEquals(PayloadCategory.GENERIC, InterestingKeyMatcher.categorize("foobar123xyz"));
    }
}
