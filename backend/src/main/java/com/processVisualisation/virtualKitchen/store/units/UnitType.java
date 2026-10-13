package com.processVisualisation.virtualKitchen.store.units;

/**
 * Unit of measure for an ingredient quantity (recipe ingredients, inventory,
 * store orders), converted between units by {@code UnitConversionService}.
 */
public enum UnitType {
    KG,
    GRAM,
    LITER,
    ML,
    /** A discrete, unit-less count (e.g. "2 eggs") rather than a weight or volume. */
    COUNT
}