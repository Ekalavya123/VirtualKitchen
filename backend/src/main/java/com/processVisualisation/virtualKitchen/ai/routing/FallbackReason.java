package com.processVisualisation.virtualKitchen.ai.routing;

/** Why {@link ModelSelectionService} routed a request to a fallback model, if it did. */
public enum FallbackReason {
    NONE,
    INSUFFICIENT_CREDITS,
    PREFERRED_MODEL_DISABLED
}
