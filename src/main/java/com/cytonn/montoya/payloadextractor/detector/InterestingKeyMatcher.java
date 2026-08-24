package com.cytonn.montoya.payloadextractor.detector;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps a normalized key name (see {@link NameNormalizer#normalForm}) to a {@link PayloadCategory}
 * by keyword matching. Order matters: entries are checked top to bottom and the first match wins,
 * so more specific keywords (e.g. "otp") are listed before more general ones (e.g. "code").
 */
public final class InterestingKeyMatcher {

    private static final Map<String, PayloadCategory> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("otp", PayloadCategory.OTP);
        KEYWORDS.put("one time", PayloadCategory.OTP);
        KEYWORDS.put("verification code", PayloadCategory.OTP);
        KEYWORDS.put("csrf", PayloadCategory.CSRF_TOKEN);
        KEYWORDS.put("xsrf", PayloadCategory.CSRF_TOKEN);
        KEYWORDS.put("session", PayloadCategory.SESSION_ID);
        KEYWORDS.put("sid", PayloadCategory.SESSION_ID);
        KEYWORDS.put("jwt", PayloadCategory.AUTH_TOKEN);
        KEYWORDS.put("bearer", PayloadCategory.AUTH_TOKEN);
        KEYWORDS.put("access token", PayloadCategory.AUTH_TOKEN);
        KEYWORDS.put("refresh token", PayloadCategory.AUTH_TOKEN);
        KEYWORDS.put("auth", PayloadCategory.AUTH_TOKEN);
        KEYWORDS.put("token", PayloadCategory.AUTH_TOKEN);
        KEYWORDS.put("api key", PayloadCategory.API_KEY);
        KEYWORDS.put("apikey", PayloadCategory.API_KEY);
        KEYWORDS.put("secret", PayloadCategory.API_KEY);
        KEYWORDS.put("password", PayloadCategory.PASSWORD);
        KEYWORDS.put("passwd", PayloadCategory.PASSWORD);
        KEYWORDS.put("pwd", PayloadCategory.PASSWORD);
        KEYWORDS.put("pin", PayloadCategory.PASSWORD);
        KEYWORDS.put("username", PayloadCategory.USERNAME);
        KEYWORDS.put("user name", PayloadCategory.USERNAME);
        KEYWORDS.put("login", PayloadCategory.USERNAME);
        KEYWORDS.put("email", PayloadCategory.EMAIL);
        KEYWORDS.put("mail", PayloadCategory.EMAIL);
        KEYWORDS.put("phone", PayloadCategory.PHONE_NUMBER);
        KEYWORDS.put("mobile", PayloadCategory.PHONE_NUMBER);
        KEYWORDS.put("msisdn", PayloadCategory.PHONE_NUMBER);
        KEYWORDS.put("uuid", PayloadCategory.UUID);
        KEYWORDS.put("guid", PayloadCategory.UUID);
        KEYWORDS.put("timestamp", PayloadCategory.TIMESTAMP);
        KEYWORDS.put("date", PayloadCategory.TIMESTAMP);
        KEYWORDS.put("time", PayloadCategory.TIMESTAMP);
        KEYWORDS.put("amount", PayloadCategory.AMOUNT);
        KEYWORDS.put("balance", PayloadCategory.AMOUNT);
        KEYWORDS.put("price", PayloadCategory.AMOUNT);
        KEYWORDS.put("total", PayloadCategory.AMOUNT);
        KEYWORDS.put("account number", PayloadCategory.ID_NUMBER);
        KEYWORDS.put("account no", PayloadCategory.ID_NUMBER);
        KEYWORDS.put("national id", PayloadCategory.ID_NUMBER);
        KEYWORDS.put("reference", PayloadCategory.ID_NUMBER);
        KEYWORDS.put("code", PayloadCategory.OTP);
        KEYWORDS.put("id", PayloadCategory.ID_NUMBER);
    }

    private InterestingKeyMatcher() {
    }

    /** Best-guess category for a raw key/path, or {@link PayloadCategory#GENERIC} if nothing matched. */
    public static PayloadCategory categorize(String rawKeyOrPath) {
        String normal = NameNormalizer.normalForm(rawKeyOrPath);
        if (normal.isEmpty()) {
            return PayloadCategory.GENERIC;
        }
        String tight = normal.replace(" ", "");
        for (Map.Entry<String, PayloadCategory> e : KEYWORDS.entrySet()) {
            String keyword = e.getKey();
            String keywordTight = keyword.replace(" ", "");
            if (normal.contains(keyword) || tight.contains(keywordTight)) {
                return e.getValue();
            }
        }
        return PayloadCategory.GENERIC;
    }

    /** True if this key is worth surfacing to the analyst by default (i.e. not GENERIC). */
    public static boolean isInteresting(String rawKeyOrPath) {
        return categorize(rawKeyOrPath) != PayloadCategory.GENERIC;
    }
}
