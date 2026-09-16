package com.processVisualisation.virtualKitchen.ai.artifact;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration for the AI artifact store, bound from {@code ai.artifact.*}.
 * <p>
 * {@link #enabled} is the feature's kill switch: turning it off makes the store a no-op
 * (no reuse, no staging) and restores the previous behaviour without a redeploy.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.artifact")
public class AiArtifactProperties {

    /** Master switch. When false, lookups miss and staging is skipped. */
    private boolean enabled = true;

    /**
     * Inline payloads larger than this are promoted to GridFS, so a runaway response can
     * never push an artifact document toward MongoDB's 16 MB limit.
     */
    private int maxInlineBytes = 16_000;

    /**
     * How long a consumer "owns" an artifact before the recovery sweeper may also try it.
     * Should comfortably exceed a normal dependent-work duration (an object-storage upload).
     */
    private long leaseTtlMs = 120_000;

    /**
     * How long the metadata row survives after the payload was successfully consumed.
     * Must stay far larger than {@link Sweeper#getFixedDelayMs()}: if the TTL index removed a
     * row before the sweeper's GC pass ran, a blob orphaned by a mid-transition crash would
     * become unreferenced forever.
     */
    private Duration consumedRetention = Duration.ofDays(7);

    /** How long an abandoned artifact's row is kept for diagnostics. */
    private Duration abandonedRetention = Duration.ofDays(30);

    private Sweeper sweeper = new Sweeper();

    /** Settings for {@link AiArtifactRecoveryJob}. */
    @Data
    public static class Sweeper {

        private boolean enabled = true;

        private long fixedDelayMs = 300_000;

        private long initialDelayMs = 60_000;

        /**
         * Grace period before a pending artifact is considered orphaned. This is the first of
         * three mechanisms keeping the sweeper away from live requests (the others being the
         * lease and the guarded status transition), so it should exceed the slowest expected
         * dependent-work duration.
         */
        private long orphanAgeMs = 120_000;

        /** Upper bound on artifacts examined per sweep, keeping each run's cost predictable. */
        private int batchSize = 25;

        /** After this many failed recoveries the artifact is abandoned and its payload dropped. */
        private int maxRecoveryAttempts = 3;
    }
}
