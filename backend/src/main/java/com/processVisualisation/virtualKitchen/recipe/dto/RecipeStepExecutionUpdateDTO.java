package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

/**
 * Request payload for updating an in-progress step execution, allowing its status
 * (as a raw string) and notes to be changed.
 */
@Data
public class RecipeStepExecutionUpdateDTO {

    private String status;
    private String notes;
}
