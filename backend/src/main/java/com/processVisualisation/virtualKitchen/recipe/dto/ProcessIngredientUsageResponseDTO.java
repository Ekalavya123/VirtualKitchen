package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessIngredientUsageResponseDTO {

    private Long id;
    private Long processExecutionId;
    private Long ingredientId;
    private double quantityUsed;
    private UnitType unit;
    private double costAtTime;
}
