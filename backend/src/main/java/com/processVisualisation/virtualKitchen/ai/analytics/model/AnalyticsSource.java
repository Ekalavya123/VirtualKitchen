package com.processVisualisation.virtualKitchen.ai.analytics.model;

/** Where an analytics record came from. */
public enum AnalyticsSource {
    /** Recorded while the request ran. */
    LIVE,
    /** Recovered from an existing {@code ai_request_jobs} row by the backfill. */
    BACKFILL_REQUEST_JOB,
    /** Recovered from an existing chat {@code ai_responses} document by the backfill. */
    BACKFILL_AI_RESPONSE
}
