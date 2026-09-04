package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeUnitType;
import lombok.Data;

@Data
public class RecipeProcessIngredientUsageRequestDTO {

    private Long processExecutionId;
    private Long ingredientId;
    private double quantityUsed;
    private RecipeUnitType unit;
    private double costAtTime;
}
