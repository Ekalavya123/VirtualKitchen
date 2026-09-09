package com.processVisualisation.virtualKitchen.recipe.model;

/**
 * Unit of measure for the quantity of an ingredient used in a recipe step, as
 * recorded on {@link RecipeIngredientUsage}.
 */
public enum UnitType {
    KG,
    GRAM,
    LITER,
    ML,
    /** A discrete, unit-less count (e.g. "2 eggs") rather than a weight or volume. */
    COUNT
}