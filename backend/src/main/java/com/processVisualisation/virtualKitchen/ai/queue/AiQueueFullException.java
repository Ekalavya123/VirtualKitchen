package com.processVisualisation.virtualKitchen.ai.queue;

import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;

/**
 * Thrown when the bounded queue for a capability is already at its
 * configured max depth. Handled by {@code GlobalExceptionHandler} as HTTP
 * 429 — the caller should retry shortly rather than the request piling up
 * indefinitely behind an unbounded backlog.
 */
public class AiQueueFullException extends RuntimeException {

    public AiQueueFullException(AiCapability capability) {
        super("The AI system is busy handling " + capability + " requests — please try again in a moment");
    }
}
