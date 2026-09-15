package com.processVisualisation.virtualKitchen.ai.queue;

import com.processVisualisation.virtualKitchen.ai.routing.ModelSelectionOutcome;

/**
 * The actual provider-calling work submitted to {@link AiRequestQueueService}.
 * Receives the already-resolved {@link ModelSelectionOutcome} so it knows
 * which client/model to invoke (via {@code ai.dispatch.AiClientResolver})
 * without deciding that itself.
 */
@FunctionalInterface
public interface AiWork<R> {
    R run(ModelSelectionOutcome outcome) throws Exception;
}
