package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

@Data
public class RecipeProcessEquipmentUsageRequestDTO {

    private Long processExecutionId;
    private Long equipmentId;
    private long usageDurationSec;
    private double costAtTime;
}
