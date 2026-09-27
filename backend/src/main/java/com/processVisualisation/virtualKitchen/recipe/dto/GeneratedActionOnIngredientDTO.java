package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One AI-generated Action On ingredient reference — semantic data only (an
 * ingredient id from the shared static catalog, or "custom" with a free-text
 * name, plus its own quantity/unit/preparation style). No presentation
 * fields: the frontend assembles this into a full {@code ActionOnIngredient}
 * (features/recipe-tool/process/model/recipeStepData.ts) once the generated result
 * is loaded into the Recipe working session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedActionOnIngredientDTO {

    private String ingredientId;
    /** Null when the recipe states no amount, or the unit is non-numeric ("to-taste", "as-needed"). */
    private Double quantity;
    /** A catalog unit id (e.g. "g", "tbsp", "clove"); legacy UnitType values (COUNT/GRAM/...) are still accepted. */
    private String unit;

    /** Blank/absent when preparation style doesn't apply to this action/ingredient. */
    private String preparationStyle;

    /** Only meaningful when {@code ingredientId} is "custom". */
    private String customIngredientName;
}
