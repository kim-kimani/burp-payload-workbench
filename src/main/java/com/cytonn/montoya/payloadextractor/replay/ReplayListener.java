package com.cytonn.montoya.payloadextractor.replay;

/** Callback interface for observing a replay run's progress. All methods may be called from a background thread. */
public interface ReplayListener {

    default void onStarted(int totalSteps) {
    }

    default void onStepCompleted(ReplayStepResult result) {
    }

    default void onPaused() {
    }

    default void onResumed() {
    }

    default void onStopped(String reason) {
    }

    default void onCompleted() {
    }
}
