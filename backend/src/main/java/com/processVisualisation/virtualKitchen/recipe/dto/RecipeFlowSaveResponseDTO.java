package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

@Data
public class RecipeFlowSaveResponseDTO {
    private String flowId;
    private String userId;
    private String message;
}
