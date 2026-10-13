package com.processVisualisation.virtualKitchen.ai.platform.analytics.model;

/** How a cost figure was obtained. */
public enum CostBasis {
    /** The provider returned a price. No current provider does. */
    PROVIDER_REPORTED,
    /** Provider-reported usage multiplied by a versioned rate card. */
    CALCULATED,
    /** An estimate: legacy figures whose rate version was never recorded, or configured infrastructure rates. */
    ESTIMATED,
    /** Usage or pricing missing; never treated as zero. */
    UNAVAILABLE
}
