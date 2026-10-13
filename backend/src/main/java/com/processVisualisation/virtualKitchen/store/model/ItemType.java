package com.processVisualisation.virtualKitchen.store.model;

import com.processVisualisation.virtualKitchen.kitchen.model.Inventory;

/**
 * Discriminates which kind of store item a polymorphic reference (e.g. in
 * {@link Inventory} or {@link ItemCost}) points to: an ingredient or a
 * piece of equipment.
 */
public enum ItemType {
    /** References an {@link Ingredient} document. */
    INGREDIENT,
    /** References an {@link Equipment} document. */
    EQUIPMENT
}
