package com.processVisualisation.virtualKitchen.ai.asset;

import java.util.Arrays;

/**
 * The kinds of global (recipe-independent) catalog resources that can own one reusable image.
 * Adding a kind later (actions, system assets) means a constant here plus a branch in
 * {@code GlobalResourceResolver}.
 */
public enum GlobalResourceType {
    /** A store catalog {@code Ingredient} (onion, salt). */
    INGREDIENT("ingredients"),
    /** A store catalog {@code Equipment} (stove, kettle). */
    EQUIPMENT("equipment");

    private final String pathSegment;

    GlobalResourceType(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    /** The URL path segment naming this type in the admin API ({@code /admin/global-assets/{segment}}). */
    public String pathSegment() {
        return pathSegment;
    }

    /**
     * The {@code VisualizationAsset.visualizationKey} of this resource's single global asset,
     * e.g. {@code global::INGREDIENT::42}. Its unique index is what keeps it to one asset per resource.
     */
    public String assetKey(Long resourceId) {
        return "global::" + name() + "::" + resourceId;
    }

    /**
     * @throws IllegalArgumentException (answered 400) when {@code segment} names no known type
     */
    public static GlobalResourceType fromPathSegment(String segment) {
        return Arrays.stream(values())
                .filter(type -> type.pathSegment.equalsIgnoreCase(segment))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown global asset type: " + segment + " (expected one of: ingredients, equipment)"));
    }
}
