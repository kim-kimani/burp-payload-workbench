package com.cytonn.montoya.payloadextractor.replay;

import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import com.cytonn.montoya.payloadextractor.modifier.RequestModifier;
import com.cytonn.montoya.payloadextractor.variables.VariableResolver;
import com.cytonn.montoya.payloadextractor.variables.VariableStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs a {@link ReplayConfig} to completion: one request per payload value, sequentially or in
 * parallel across a configurable number of worker threads, with pause/resume/stop and an optional
 * "stop as soon as any response matches this status code" guard rail (useful for things like
 * brute-forcing an OTP where a 200 instead of a 401 means "found it, stop hammering the endpoint").
 *
 * <p>One engine instance runs one replay at a time; call {@link #run(ReplayConfig)} again (after
 * it returns) to start a fresh run - state is reset at the top of each call.
 */
public final class ReplayEngine {

    private enum State { IDLE, RUNNING, PAUSED, STOPPED }

    private final Http http;
    private final ReplayListener listener;
    private final VariableStore variableStore;

    private volatile State state = State.IDLE;
    private final Object pauseLock = new Object();

    public ReplayEngine(Http http, ReplayListener listener) {
        this(http, listener, null);
    }

    /** @param variableStore when non-null, {@code {{NAME}}} placeholders in the substituted request are resolved (see {@link VariableResolver}) right before each request is sent. */
    public ReplayEngine(Http http, ReplayListener listener, VariableStore variableStore) {
        this.http = http;
        this.listener = listener == null ? new ReplayListener() {} : listener;
        this.variableStore = variableStore;
    }

    public void run(ReplayConfig config) {
        state = State.RUNNING;
        List<String> values = orderedValues(config);
        int total = Math.min(values.size(), config.effectiveStepCount());
        listener.onStarted(total);

        AtomicBoolean stopFlag = new AtomicBoolean(false);
        AtomicReference<String> stopReason = new AtomicReference<>();

        if (config.isParallel() && total > 1) {
            runParallel(config, values, total, stopFlag, stopReason);
        } else {
            runSequential(config, values, total, stopFlag, stopReason);
        }

        if (state != State.STOPPED) {
            state = State.IDLE;
            listener.onCompleted();
        } else {
            listener.onStopped(stopReason.get() == null ? "Stopped by user" : stopReason.get());
        }
    }

    public void pause() {
        if (state == State.RUNNING) {
            state = State.PAUSED;
            listener.onPaused();
        }
    }

    public void resume() {
        if (state == State.PAUSED) {
            synchronized (pauseLock) {
                state = State.RUNNING;
                pauseLock.notifyAll();
            }
            listener.onResumed();
        }
    }

    public void stop() {
        state = State.STOPPED;
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    // ---------------------------------------------------------------- sequential

    private void runSequential(ReplayConfig config, List<String> values, int total, AtomicBoolean stopFlag, AtomicReference<String> stopReason) {
        for (int i = 0; i < total; i++) {
            awaitIfPaused();
            if (state == State.STOPPED) {
                return;
            }
            ReplayStepResult result = executeStep(config, i, values.get(i));
            listener.onStepCompleted(result);
            if (shouldStopOnStatus(config, result)) {
                state = State.STOPPED;
                stopReason.set("Stop condition matched: status " + result.statusCode() + " at step " + i);
                return;
            }
            if (config.delayMillisBetweenRequests() > 0 && i < total - 1) {
                sleep(config.delayMillisBetweenRequests());
            }
        }
    }

    // ---------------------------------------------------------------- parallel

    private void runParallel(ReplayConfig config, List<String> values, int total, AtomicBoolean stopFlag, AtomicReference<String> stopReason) {
        int workers = Math.max(1, Math.min(config.concurrency(), total));
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        AtomicInteger nextStep = new AtomicInteger(0);

        List<Runnable> tasks = new ArrayList<>();
        for (int w = 0; w < workers; w++) {
            tasks.add(() -> {
                while (true) {
                    if (state == State.STOPPED || stopFlag.get()) {
                        return;
                    }
                    awaitIfPaused();
                    if (state == State.STOPPED || stopFlag.get()) {
                        return;
                    }
                    int i = nextStep.getAndIncrement();
                    if (i >= total) {
                        return;
                    }
                    ReplayStepResult result = executeStep(config, i, values.get(i));
                    synchronized (listener) {
                        listener.onStepCompleted(result);
                    }
                    if (shouldStopOnStatus(config, result)) {
                        stopFlag.set(true);
                        stopReason.compareAndSet(null, "Stop condition matched: status " + result.statusCode() + " at step " + i);
                        state = State.STOPPED;
                        return;
                    }
                }
            });
        }

        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (Runnable t : tasks) {
                futures.add(pool.submit(t));
            }
            for (java.util.concurrent.Future<?> f : futures) {
                f.get();
            }
        } catch (Exception e) {
            stopReason.compareAndSet(null, "Replay worker error: " + e.getMessage());
        } finally {
            pool.shutdownNow();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ---------------------------------------------------------------- shared

    private ReplayStepResult executeStep(ReplayConfig config, int index, String value) {
        long start = System.currentTimeMillis();
        try {
            HttpRequest request = RequestModifier.substituteSingleValue(config.baseRequest(), config.targetField(), value);
            if (variableStore != null) {
                request = VariableResolver.resolveInRequest(request, variableStore);
            }
            HttpRequestResponse rr = http.sendRequest(request);
            long elapsed = System.currentTimeMillis() - start;
            return ReplayStepResult.success(index, value, rr, elapsed);
        } catch (Exception e) {
            return ReplayStepResult.failure(index, value, e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private boolean shouldStopOnStatus(ReplayConfig config, ReplayStepResult result) {
        return config.stopOnStatusCode() != null && result.statusCode() != null
                && config.stopOnStatusCode().intValue() == result.statusCode().intValue();
    }

    private void awaitIfPaused() {
        synchronized (pauseLock) {
            while (state == State.PAUSED) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<String> orderedValues(ReplayConfig config) {
        List<String> values = new ArrayList<>(config.payloadValues());
        if (config.order() == ReplayOrder.RANDOM) {
            Collections.shuffle(values);
        }
        return values;
    }
}
