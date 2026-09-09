package com.processVisualisation.virtualKitchen.common.utils;

import java.util.Map;
import java.util.Objects;

/**
 * Builds a canonical, reusable key for a recipe step's visualization asset.
 * Only fields that affect what the visualization actually looks like are included
 * (action + ingredient + preparationStyle). Quantity, unit, notes and duration are
 * intentionally excluded since they don't change the visual representation.
 */
public final class VisualizationKeyBuilder {

    private static final String SEPARATOR = "::";
    private static final String UNSPECIFIED = "unspecified";

    private VisualizationKeyBuilder() {
    }

    /**
     * Builds the canonical cache key for a recipe step's visualization asset
     * from its three visually-relevant fields. Each field is independently
     * normalized (trimmed, lower-cased, whitespace collapsed to hyphens, and
     * defaulted to {@code "unspecified"} when blank/{@code null}) before
     * being joined with {@code "::"}.
     *
     * @param action the cooking action performed in the step (e.g. "chop", "boil")
     * @param ingredient the ingredient the action is performed on
     * @param preparationStyle the preparation style applied (e.g. "diced", "julienned")
     * @return the normalized {@code action::ingredient::preparationStyle} cache key
     */
    public static String build(String action, String ingredient, String preparationStyle) {
        return String.join(
                SEPARATOR,
                normalize(action),
                normalize(ingredient),
                normalize(preparationStyle));
    }

    /**
     * Convenience overload of {@link #build(String, String, String)} that
     * extracts the action, ingredient and preparation style from a raw
     * node-data map, as used by the recipe execution graph. Falls back
     * between alternative fields when the primary one is blank (e.g.
     * {@code ingredientId} then {@code customIngredientName}), and unwraps
     * a nested {@code "step"} object if present (see {@link #stepFields}).
     *
     * @param data the node's raw data map, optionally nesting its fields under a {@code "step"} key
     * @return the normalized visualization cache key derived from {@code data}
     */
    public static String buildFromNodeData(Map<String, Object> data) {
        Map<String, Object> step = stepFields(data);
        return build(
                stringValue(step.get("action")),
                firstNonBlank(step.get("ingredientId"), step.get("customIngredientName")),
                firstNonBlank(step.get("preparationStyle"), step.get("customPreparationStyle"), step.get("preparationStyleId")));
    }

    /**
     * Recipe step nodes nest their visual fields under a "step" object
     * (see RecipeExecutionGraphConverter on the frontend); fall back to the
     * top-level map for callers that already pass the unwrapped step data.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> stepFields(Map<String, Object> data) {
        if (data == null) {
            return Map.of();
        }
        Object nested = data.get("step");
        return nested instanceof Map ? (Map<String, Object>) nested : data;
    }

    private static String firstNonBlank(Object... values) {
        for (Object value : values) {
            String stringValue = stringValue(value);
            if (stringValue != null && !stringValue.trim().isEmpty()) {
                return stringValue;
            }
        }
        return null;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String normalize(String value) {
        if (value == null) {
            return UNSPECIFIED;
        }
        String trimmed = Objects.requireNonNull(value).trim().toLowerCase();
        if (trimmed.isEmpty()) {
            return UNSPECIFIED;
        }
        return trimmed.replaceAll("\\s+", "-");
    }
}
