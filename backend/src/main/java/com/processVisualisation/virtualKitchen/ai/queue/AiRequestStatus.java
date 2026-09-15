package com.processVisualisation.virtualKitchen.ai.queue;

/**
 * Lifecycle states of a single {@link AiRequestJob}:
 * {@code QUEUED -> PROCESSING -> (COMPLETED | FAILED)}, with {@code CANCELLED}
 * reserved for a job that was created but withdrawn before processing (e.g. a
 * future admission-control sweep) — no code path sets it yet.
 */
public enum AiRequestStatus {
    QUEUED,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}
