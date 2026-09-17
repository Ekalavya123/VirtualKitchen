package com.processVisualisation.virtualKitchen.common.utils;

/**
 * Builds the canonical, unique key for one recipe step's visualization asset.
 * <p>
 * Previously this was derived from content fields (action + ingredient +
 * preparation style), intended as a cross-recipe cache key so identical-looking
 * steps could share one generated image. In practice that was inconsistent:
 * two unrelated steps with sparse/blank fields (e.g. an action with no
 * ingredient or preparation style set) normalized to the same generic key —
 * {@code "mix::unspecified::unspecified"} — and ended up incorrectly sharing
 * one step's image with a completely different step, possibly in a different
 * recipe entirely.
 * <p>
 * The key is now scoped to exactly one step of exactly one recipe
 * ({@code recipeId::stepId}), so two steps never collide and never share a
 * cached asset — the only case that reuses an existing {@code
 * VisualizationAsset} is re-visualizing the same step of the same recipe.
 */
public final class VisualizationKeyBuilder {

    private static final String SEPARATOR = "::";

    private VisualizationKeyBuilder() {
    }

    /**
     * Builds the visualization asset key for one step of one recipe.
     *
     * @param recipeId the recipe flow's id (e.g. {@code Recipe.flowId})
     * @param stepId the step node's id within that recipe (e.g. {@code Recipe.NodeDocument.id})
     * @return the {@code recipeId::stepId} key, unique per (recipe, step) pair
     */
    public static String build(String recipeId, String stepId) {
        return normalize(recipeId) + SEPARATOR + normalize(stepId);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
