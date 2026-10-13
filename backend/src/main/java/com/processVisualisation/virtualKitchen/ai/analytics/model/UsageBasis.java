package com.processVisualisation.virtualKitchen.ai.analytics.model;

/** How a usage figure was obtained. */
public enum UsageBasis {
    PROVIDER_REPORTED,
    /** The provider reported no counts (local engines, or nothing recorded historically). */
    NOT_REPORTED,
    /** Measured locally, e.g. the duration of a returned WAV. */
    DERIVED_LOCAL
}
