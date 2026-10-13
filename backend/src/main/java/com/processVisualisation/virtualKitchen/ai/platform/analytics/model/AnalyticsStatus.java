package com.processVisualisation.virtualKitchen.ai.platform.analytics.model;

/** Lifecycle of a logical AI request (and the outcome of one attempt). */
public enum AnalyticsStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT,
    /** Supported by the schema; nothing in the application cancels AI requests today. */
    CANCELLED,
    /** Never ran: queue full, or no model available. */
    REJECTED,
    /** Served from the artifact store; no provider call, no credits. */
    REUSED;

    public boolean isTerminal() {
        return this != QUEUED && this != RUNNING;
    }
}
