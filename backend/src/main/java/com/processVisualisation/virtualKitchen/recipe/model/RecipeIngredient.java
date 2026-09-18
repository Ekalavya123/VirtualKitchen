package com.processVisualisation.virtualKitchen.recipe.model;

import lombok.Data;

/**
 * Embedded line item on a {@link RecipeTemplate} recording how much of a
 * global {@code com.processVisualisation.virtualKitchen.store.model.Ingredient}
 * catalog entry the recipe uses, plus recipe-specific quantity/notes/prep
 * info. Deliberately holds only a reference ({@link #ingredientId}) rather
 * than a copy of the ingredient's name/description/image — that master data
 * (including its image asset) stays on the global catalog entry so it stays
 * reusable and consistent across every recipe that references it.
 */
@Data
public class RecipeIngredient {

    private Long ingredientId;

    private double quantity;
    private UnitType unit;

    private String notes;

    /** Recipe-specific preparation detail for this ingredient (e.g. "diced", "room temperature"). */
    private String preparation;
}
