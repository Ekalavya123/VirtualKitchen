package com.processVisualisation.virtualKitchen.recipe.model;

import lombok.Data;

/**
 * Embedded, recipe-level nutrition summary on a {@link RecipeTemplate}.
 * Every field is author-entered and optional (nullable); there is no
 * per-step nutrition calculation or derivation from {@link RecipeIngredient}
 * quantities in this phase.
 */
@Data
public class NutritionInfo {
    private Double calories;
    private Double proteinGrams;
    private Double carbohydratesGrams;
    private Double fatGrams;
    private Double fiberGrams;
    private Double sodiumMilligrams;
    private Integer servings;
}
