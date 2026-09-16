package com.processVisualisation.virtualKitchen.ai.artifact.consumer;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;

/**
 * The dependent work that turns a stored AI payload into its finished form — uploading a
 * generated image to object storage and recording the resulting URL, for example.
 * <p>
 * This is an interface rather than a lambda at the call site for one reason: it must be
 * re-drivable. {@code AiArtifactRecoveryJob} recovers an artifact whose consumer never
 * completed — possibly in a later process, after a crash — and has no closure to resurrect.
 * It resolves the implementation by {@link #consumerId()} instead.
 * <p>
 * Implementations must be <b>idempotent enough to run twice</b>. Under an expired lease the
 * request path and the sweeper can both run, and only one will win the guarded transition in
 * {@code AiArtifactService.markConsumed}; the loser's side effects must be harmless.
 * <p>
 * The request path calls the same bean the sweeper does, so online and recovery behaviour
 * cannot drift apart.
 */
public interface AiArtifactConsumer {

    /** Stable identifier persisted as {@code AiArtifact.consumerId}. */
    String consumerId();

    /**
     * Performs the dependent work over an already-persisted payload.
     * <p>
     * Throwing leaves the artifact {@code PENDING} with its payload intact, which is exactly
     * the desired outcome: the paid result is retained and the work is retried later, rather
     * than the result being lost and regenerated.
     *
     * @param artifact the artifact row, carrying the correlation and producing-model metadata
     * @param payload the decoded payload, of whatever type the artifact's codec produces
     * @return a short description of the result (e.g. the stored object's URL), for logging
     *         and for the caller to attach to its own domain object
     * @throws Exception if the dependent work fails
     */
    String consume(AiArtifact artifact, Object payload) throws Exception;
}
