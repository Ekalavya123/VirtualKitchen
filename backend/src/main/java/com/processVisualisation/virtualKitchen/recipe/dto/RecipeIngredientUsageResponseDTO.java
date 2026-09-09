package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Builder;
import lombok.Data;

/**
 * Response payload representing a persisted record of ingredient usage during a
 * recipe execution, including its generated id, quantity used, {@link UnitType}
 * and cost at the time.
 */
@Data
@Builder
public class RecipeIngredientUsageResponseDTO {

    private Long id;
    private Long processExecutionId;
    private Long ingredientId;
    private double quantityUsed;
    private UnitType unit;
    private double costAtTime;
}
