package com.processVisualisation.virtualKitchen.ai.queue;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactHit;
import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionOutcome;

/**
 * The result of a completed {@link AiRequestQueueService} execution: the job id, the work's
 * return value, which model served it, and — when the call site opted into the AI artifact
 * store — whether the value came from a stored payload rather than a fresh provider call.
 *
 * @param jobId the {@code AiRequestJob} that was billed for this payload. For a reused payload
 *              this is the <em>original</em> producing job, so the audit trail still points at
 *              the call that was actually paid for.
 * @param value the work's result
 * @param selection which model served it. For a reused payload this is reconstructed from the
 *                  artifact's recorded metadata and carries a {@code null} reservation, so
 *                  nothing downstream can consume or release credits for it.
 * @param reused true when this value came from the artifact store — no provider call, no credits
 * @param artifact the staged or reused artifact, or {@code null} when the call site did not opt in
 */
public record AiRequestOutcome<R>(
        String jobId,
        R value,
        ModelSelectionOutcome selection,
        boolean reused,
        AiArtifact artifact
) {

    /** A value produced by an actual provider call, optionally staged in the artifact store. */
    public static <R> AiRequestOutcome<R> fresh(String jobId, R value, ModelSelectionOutcome selection, AiArtifact artifact) {
        return new AiRequestOutcome<>(jobId, value, selection, false, artifact);
    }

    /** A value served from the artifact store; no provider call was made and no credit was spent. */
    public static <R> AiRequestOutcome<R> reused(AiArtifactHit<R> hit, ModelSelectionOutcome selection) {
        return new AiRequestOutcome<>(hit.artifact().getProducingJobId(), hit.value(), selection, true, hit.artifact());
    }
}
