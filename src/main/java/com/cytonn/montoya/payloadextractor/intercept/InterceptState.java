package com.cytonn.montoya.payloadextractor.intercept;

/** Lifecycle state of one row in the Intercept tab's REQUEST HISTORY table. */
public enum InterceptState {
    WAITING,
    FORWARDED,
    EDITED_AND_FORWARDED,
    DROPPED,
    AUTO_FORWARDED
}
