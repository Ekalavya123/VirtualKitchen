package com.processVisualisation.virtualKitchen.ai.analytics;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters that make analytics write failures observable (exposed on the admin health endpoint and logged), so a
 * gap in the analytics data is never silent.
 */
@Component
public class AnalyticsHealth {

    private final AtomicLong writeFailures = new AtomicLong();
    private final AtomicLong retriedOk = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private volatile Instant lastFailureAt;
    private volatile String lastFailure;

    void failure(String operation, Exception error) {
        writeFailures.incrementAndGet();
        lastFailureAt = Instant.now();
        lastFailure = operation + ": " + error.getClass().getSimpleName();
    }

    void retrySucceeded() {
        retriedOk.incrementAndGet();
    }

    void dropped() {
        dropped.incrementAndGet();
    }

    public Snapshot snapshot(int pendingRetries) {
        return new Snapshot(writeFailures.get(), retriedOk.get(), dropped.get(), pendingRetries, lastFailureAt, lastFailure);
    }

    /**
     * @param writeFailures write attempts that failed (each is queued for retry)
     * @param retriedOk     queued writes that later succeeded
     * @param dropped       writes given up on, or not queued because the retry buffer was full: data really lost
     */
    public record Snapshot(long writeFailures, long retriedOk, long dropped, int pendingRetries,
                           Instant lastFailureAt, String lastFailure) {
    }
}
