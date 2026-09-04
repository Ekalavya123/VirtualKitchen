package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeUnitType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecipeProcessIngredientUsageResponseDTO {

    private Long id;
    private Long processExecutionId;
    private Long ingredientId;
    private double quantityUsed;
    private RecipeUnitType unit;
    private double costAtTime;
}
