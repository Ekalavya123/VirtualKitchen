package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response payload representing a persisted record of equipment usage during a
 * recipe execution, including its generated id, duration used and cost at the time.
 */
@Data
@Builder
public class RecipeEquipmentUsageResponseDTO {

    private Long id;
    private Long processExecutionId;
    private Long equipmentId;
    private long usageDurationSec;
    private double costAtTime;
}
