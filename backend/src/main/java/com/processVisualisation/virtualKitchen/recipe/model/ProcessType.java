package com.processVisualisation.virtualKitchen.recipe.model;

/**
 * Distinguishes a recipe's single top-level {@link Process} ({@code MAIN})
 * from a nested process referenced by a {@code PROCESS}-kind
 * {@link ProcessNodeKind} node ({@code SUBPROCESS}).
 */
public enum ProcessType {
    MAIN,
    SUBPROCESS
}
