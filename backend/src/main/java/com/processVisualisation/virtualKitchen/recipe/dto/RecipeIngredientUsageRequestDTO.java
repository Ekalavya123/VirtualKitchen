package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Data;

@Data
public class RecipeIngredientUsageRequestDTO {

    private Long processExecutionId;
    private Long ingredientId;
    private double quantityUsed;
    private UnitType unit;
    private double costAtTime;
}
