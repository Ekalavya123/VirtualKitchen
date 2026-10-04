package com.processVisualisation.virtualKitchen.ai.narration.model;

/**
 * Lifecycle of a step narration as seen by API clients.
 * <p>
 * Only {@link #GENERATING}, {@link #READY} and {@link #FAILED} are ever persisted on a
 * {@link StepNarration}. {@link #NOT_GENERATED} (no usable record) and {@link #STALE} (a record
 * whose source hash no longer matches the step's current text) are derived on every read, so an
 * edit in the recipe editor invalidates narration without the editor doing anything.
 */
public enum StepNarrationStatus {
    NOT_GENERATED,
    GENERATING,
    READY,
    STALE,
    FAILED
}
