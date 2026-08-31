package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecipeVisualizationStepResponseDTO {
    private String stepId;
    private Long visualizationAssetId;
    private String imagePrompt;
    private String imageUrl;
}
