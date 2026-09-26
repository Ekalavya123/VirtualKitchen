package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One AI-generated Action On ingredient reference — semantic data only (an
 * ingredient id from the shared static catalog, or "custom" with a free-text
 * name, plus its own quantity/unit/preparation style). No presentation
 * fields: the frontend assembles this into a full {@code ActionOnIngredient}
 * (features/flow-editor/model/processStepData.ts) once the generated result
 * is loaded into the Recipe working session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedActionOnIngredientDTO {

    private String ingredientId;
    private Double quantity;
    private String unit;

    /** Blank/absent when preparation style doesn't apply to this action/ingredient. */
    private String preparationStyle;

    /** Only meaningful when {@code ingredientId} is "custom". */
    private String customIngredientName;
}
