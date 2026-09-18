package com.processVisualisation.virtualKitchen.recipe.model;

/**
 * Discriminates what a {@link Process}'s node represents: a leaf cooking
 * action ({@code STEP}) or a branch ({@code CONDITION}). A {@link Process}
 * graph never embeds another process as a node — a subprocess is referenced
 * only by id, from a STEP's own "Action On" data (see {@code Process.output}
 * and the STEP data shape, both frontend/opaque-data concerns from this
 * model's point of view).
 */
public enum ProcessNodeKind {
    STEP,
    CONDITION
}
