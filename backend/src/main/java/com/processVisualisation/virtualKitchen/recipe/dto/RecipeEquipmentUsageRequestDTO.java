package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

/**
 * Request payload for recording a piece of equipment's usage during a recipe
 * execution, capturing which execution/equipment it applies to along with the
 * usage duration and cost incurred at that time.
 */
@Data
public class RecipeEquipmentUsageRequestDTO {

    private Long processExecutionId;
    private Long equipmentId;
    private long usageDurationSec;
    private double costAtTime;
}
