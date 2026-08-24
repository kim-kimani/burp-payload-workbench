package com.cytonn.montoya.payloadextractor.modifier;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.cytonn.montoya.payloadextractor.detector.PayloadDetector;
import com.cytonn.montoya.payloadextractor.parser.FieldLocation;
import com.cytonn.montoya.payloadextractor.parser.MessageDirection;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;
import com.cytonn.montoya.payloadextractor.testutil.FakeHttpRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the v1.3.0 fix end to end: Add / Duplicate / drag-reorder / remove on the Workbench's
 * field list produce a REAL change in the composed request's JSON body / Cookie header / other
 * headers - not just a cosmetic reorder of on-screen boxes.
 */
class RequestModifierTest {

    @Test
    void jsonBodyAddRemoveReorderSubstituteAllApplyForReal() {
        String body = "{\"username\":\"bob\",\"otp\":\"111111\",\"session\":\"abc123\"}";
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        HttpRequest original = FakeHttpRequest.create(body, headers);

        List<ParsedField> baseline = PayloadDetector.detectRequest(original);
        List<ParsedField> baselineJson = filter(baseline, FieldLocation.JSON_BODY);
        assertEquals(3, baselineJson.size());

        List<ParsedField> working = new ArrayList<>();
        ParsedField session = findByRawKey(baselineJson, "session").copyForWorking();
        ParsedField otp = findByRawKey(baselineJson, "otp").copyForWorking();
        otp.setCurrentValue("999999");
        ParsedField csrf = ParsedField.builder()
                .location(FieldLocation.JSON_BODY).direction(MessageDirection.REQUEST)
                .rawKey("csrfToken").path("csrfToken").originalValue("").manuallyAdded(true).name("csrfToken").build();
        csrf.setCurrentValue("NEWTOKEN");
        working.add(session);
        working.add(otp);
        working.add(csrf);
        // "username" intentionally omitted -> must be removed from the real body

        HttpRequest composed = RequestModifier.buildComposedRequest(original, baseline, working);
        String composedBody = composed.bodyToString();

        assertFalse(composedBody.contains("username"), "removed field must not survive in the real body");
        assertTrue(composedBody.contains("\"session\":\"abc123\""));
        assertTrue(composedBody.contains("\"otp\":\"999999\""), "edited value must be substituted in the real body");
        assertTrue(composedBody.contains("\"csrfToken\":\"NEWTOKEN\""), "added field must appear in the real body");
        assertTrue(composedBody.indexOf("session") < composedBody.indexOf("otp"));
        assertTrue(composedBody.indexOf("otp") < composedBody.indexOf("csrfToken"), "drag order must be the real key order");
    }

    @Test
    void cookieAddRemoveReorderSubstituteAllApplyForReal() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Cookie", "session=abc; theme=dark; lang=en");
        HttpRequest original = FakeHttpRequest.create("", headers);

        List<ParsedField> baseline = PayloadDetector.detectRequest(original);
        List<ParsedField> baselineCookies = filter(baseline, FieldLocation.COOKIE);
        assertEquals(3, baselineCookies.size());

        List<ParsedField> working = new ArrayList<>();
        ParsedField theme = findByRawKey(baselineCookies, "theme").copyForWorking();
        ParsedField session = findByRawKey(baselineCookies, "session").copyForWorking();
        session.setCurrentValue("XYZ999");
        ParsedField csrf = ParsedField.builder()
                .location(FieldLocation.COOKIE).direction(MessageDirection.REQUEST)
                .rawKey("csrf").path("csrf").headerName("Cookie").originalValue("").manuallyAdded(true).name("csrf").build();
        csrf.setCurrentValue("TOKEN1");
        working.add(theme);
        working.add(csrf);
        working.add(session);
        // "lang" intentionally omitted -> must be removed from the real Cookie header

        HttpRequest composed = RequestModifier.buildComposedRequest(original, baseline, working);
        String cookieHeader = composed.header("Cookie").value();

        assertFalse(cookieHeader.contains("lang="));
        assertTrue(cookieHeader.contains("session=XYZ999"));
        assertTrue(cookieHeader.contains("csrf=TOKEN1"));
        assertTrue(cookieHeader.indexOf("theme") < cookieHeader.indexOf("csrf"));
        assertTrue(cookieHeader.indexOf("csrf") < cookieHeader.indexOf("session"));
    }

    @Test
    void headerAddRemoveSubstituteApplyForReal() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer old-token");
        headers.put("X-Custom", "keep-me");
        HttpRequest original = FakeHttpRequest.create("", headers);

        List<ParsedField> baseline = PayloadDetector.detectRequest(original);
        List<ParsedField> baselineHeaders = filter(baseline, FieldLocation.HEADER);
        assertEquals(2, baselineHeaders.size());

        List<ParsedField> working = new ArrayList<>();
        ParsedField auth = findByRawKey(baselineHeaders, "Authorization").copyForWorking();
        auth.setCurrentValue("Bearer new-token");
        working.add(auth);
        ParsedField injected = ParsedField.builder()
                .location(FieldLocation.HEADER).direction(MessageDirection.REQUEST)
                .rawKey("X-Injected").headerName("X-Injected").originalValue("").manuallyAdded(true).name("X-Injected").build();
        injected.setCurrentValue("hello");
        working.add(injected);
        // "X-Custom" intentionally omitted -> must be removed

        HttpRequest composed = RequestModifier.buildComposedRequest(original, baseline, working);

        assertFalse(composed.hasHeader("X-Custom"));
        assertEquals("Bearer new-token", composed.header("Authorization").value());
        assertEquals("hello", composed.header("X-Injected").value());
    }

    private static ParsedField findByRawKey(List<ParsedField> fields, String rawKey) {
        return fields.stream().filter(f -> f.rawKey().equals(rawKey)).findFirst().orElseThrow();
    }

    private static List<ParsedField> filter(List<ParsedField> fields, FieldLocation loc) {
        List<ParsedField> out = new ArrayList<>();
        for (ParsedField f : fields) if (f.location() == loc) out.add(f);
        return out;
    }
}
