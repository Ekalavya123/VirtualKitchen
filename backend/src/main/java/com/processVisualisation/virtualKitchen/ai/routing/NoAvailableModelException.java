package com.processVisualisation.virtualKitchen.ai.routing;

import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;

/**
 * Thrown when a user's preferred/default model requires credits they don't
 * have, and no enabled fallback model is configured for the capability.
 * Handled by {@code GlobalExceptionHandler} as HTTP 503 — this failure is
 * scoped to the single AI operation that triggered it and never blocks any
 * other feature.
 */
public class NoAvailableModelException extends RuntimeException {

    public NoAvailableModelException(AiCapability capability) {
        super("No available AI model for " + capability
                + ": premium credits are exhausted and no standard model is configured as a fallback");
    }
}
