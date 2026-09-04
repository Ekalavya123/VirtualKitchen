package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeVisualizationStepResponseDTO {
    private String stepId;
    private Long visualizationAssetId;
    private String imagePrompt;
    private String imageUrl;
}

