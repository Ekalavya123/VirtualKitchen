package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Data;

/**
 * Request payload for recording an ingredient's usage during a recipe execution,
 * capturing the quantity consumed, its {@link UnitType} and the cost at that time.
 */
@Data
public class RecipeIngredientUsageRequestDTO {

    private Long processExecutionId;
    private Long ingredientId;
    private double quantityUsed;
    private UnitType unit;
    private double costAtTime;
}
