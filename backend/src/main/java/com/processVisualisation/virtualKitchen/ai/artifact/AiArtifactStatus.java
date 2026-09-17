package com.processVisualisation.virtualKitchen.ai.artifact;

/**
 * Lifecycle of one {@link AiArtifact} — a paid AI payload held between the provider
 * call that produced it and the dependent work that consumes it.
 * <p>
 * There is deliberately no {@code RECOVERING} state: concurrency between the request
 * path and {@code AiArtifactRecoveryJob} is guarded by {@link AiArtifact#getLeaseUntil()},
 * not by status. A lease held by an instance that then crashes self-heals by expiry,
 * whereas a status would need a repair pass to clear.
 */
public enum AiArtifactStatus {

    /**
     * Payload is persisted and the dependent work has not confirmed success.
     * Reusable by the request path, and eligible for the recovery sweeper.
     * {@link AiArtifact#getExpiresAt()} is null in this state, so a pending
     * artifact can never be removed by the TTL index no matter how long it waits.
     */
    PENDING,

    /**
     * Dependent work succeeded. The payload has been deleted; only the metadata row
     * remains, expiring at {@link AiArtifact#getExpiresAt()}.
     */
    CONSUMED,

    /**
     * Recovery gave up after {@code ai.artifact.sweeper.max-recovery-attempts}.
     * The payload has been deleted; the row is retained for diagnostics until
     * {@link AiArtifact#getExpiresAt()}, with the last failure in
     * {@link AiArtifact#getLastError()}.
     */
    ABANDONED
}
