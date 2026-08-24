package com.cytonn.montoya.payloadextractor.replay;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.cytonn.montoya.payloadextractor.parser.FieldLocation;
import com.cytonn.montoya.payloadextractor.parser.MessageDirection;
import com.cytonn.montoya.payloadextractor.parser.ParsedField;
import com.cytonn.montoya.payloadextractor.testutil.FakeHttp;
import com.cytonn.montoya.payloadextractor.testutil.FakeHttpRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises sequential and parallel replay (v1.1.0's "make it powerful" concurrency feature) plus stop-on-status. */
class ReplayEngineTest {

    private static ParsedField otpField() {
        return ParsedField.builder()
                .location(FieldLocation.JSON_BODY).direction(MessageDirection.REQUEST)
                .rawKey("otp").path("otp").originalValue("000000").name("otp").build();
    }

    private static HttpRequest baseRequest() {
        Map<String, String> headers = new LinkedHashMap<>();
        return FakeHttpRequest.create("{\"otp\":\"000000\"}", headers);
    }

    private static String extractOtp(HttpRequest req) {
        String body = req.bodyToString();
        int i = body.indexOf("\"otp\":\"");
        if (i < 0) return null;
        int start = i + 7;
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }

    @Test
    void sequentialReplayRunsEveryStepInOrder() {
        Http http = FakeHttp.create(req -> 403);
        List<ReplayStepResult> results = new CopyOnWriteArrayList<>();
        AtomicInteger completed = new AtomicInteger(0);

        ReplayConfig config = ReplayConfig.builder()
                .baseRequest(baseRequest()).targetField(otpField())
                .payloadValues(List.of("1", "2", "3", "4", "5"))
                .order(ReplayOrder.SEQUENTIAL).parallel(false).build();

        new ReplayEngine(http, new ReplayListener() {
            public void onStepCompleted(ReplayStepResult r) { results.add(r); }
            public void onCompleted() { completed.incrementAndGet(); }
        }).run(config);

        assertEquals(5, results.size());
        assertEquals(1, completed.get());
        assertEquals("1", results.get(0).payloadValue());
        assertEquals("5", results.get(4).payloadValue());
    }

    @Test
    void parallelReplayRunsEveryStepExactlyOnce() {
        Http http = FakeHttp.create(req -> 403);
        List<ReplayStepResult> results = new CopyOnWriteArrayList<>();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 30; i++) values.add(String.valueOf(i));

        ReplayConfig config = ReplayConfig.builder()
                .baseRequest(baseRequest()).targetField(otpField())
                .payloadValues(values).parallel(true).concurrency(8).build();

        new ReplayEngine(http, new ReplayListener() {
            public void onStepCompleted(ReplayStepResult r) { results.add(r); }
        }).run(config);

        assertEquals(30, results.size());
        Set<String> distinct = new HashSet<>();
        for (ReplayStepResult r : results) distinct.add(r.payloadValue());
        assertEquals(30, distinct.size(), "no step should run twice or be skipped under concurrency");
    }

    @Test
    void stopOnStatusSequentialStopsAsSoonAsItMatches() {
        Http http = FakeHttp.create(req -> "3".equals(extractOtp(req)) ? 200 : 403);
        List<ReplayStepResult> results = new CopyOnWriteArrayList<>();
        AtomicReference<String> stopReason = new AtomicReference<>();

        ReplayConfig config = ReplayConfig.builder()
                .baseRequest(baseRequest()).targetField(otpField())
                .payloadValues(List.of("1", "2", "3", "4", "5"))
                .parallel(false).stopOnStatusCode(200).build();

        new ReplayEngine(http, new ReplayListener() {
            public void onStepCompleted(ReplayStepResult r) { results.add(r); }
            public void onStopped(String reason) { stopReason.set(reason); }
        }).run(config);

        assertEquals(3, results.size(), "must stop exactly at the third value (\"3\")");
        assertNotNull(stopReason.get());
    }

    @Test
    void stopOnStatusParallelStopsWellShortOfExhaustingAllSteps() {
        Http http = FakeHttp.create(req -> "50".equals(extractOtp(req)) ? 200 : 403);
        List<ReplayStepResult> results = new CopyOnWriteArrayList<>();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < 200; i++) values.add(String.valueOf(i));

        ReplayConfig config = ReplayConfig.builder()
                .baseRequest(baseRequest()).targetField(otpField())
                .payloadValues(values).parallel(true).concurrency(10).stopOnStatusCode(200).build();

        new ReplayEngine(http, new ReplayListener() {
            public void onStepCompleted(ReplayStepResult r) { results.add(r); }
        }).run(config);

        assertTrue(results.size() < 200, "stop-on-status should short-circuit remaining parallel work");
        assertTrue(results.stream().anyMatch(r -> "50".equals(r.payloadValue())), "the matching value must have actually been tried");
    }
}
