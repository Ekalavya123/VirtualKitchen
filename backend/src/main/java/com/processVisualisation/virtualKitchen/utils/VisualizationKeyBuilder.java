package com.processVisualisation.virtualKitchen.utils;

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

    public static String build(String action, String ingredient, String preparationStyle) {
        return String.join(
                SEPARATOR,
                normalize(action),
                normalize(ingredient),
                normalize(preparationStyle));
    }

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
