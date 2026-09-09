package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Represents the AI-generated visualization result for a single recipe step,
 * including the prompt used and the resulting image asset/URL. Nested within
 * {@link RecipeVisualizationResponseDTO}.
 */
@Data
@Builder
public class RecipeVisualizationStepResponseDTO {
    private String stepId;
    private Long visualizationAssetId;
    private String imagePrompt;
    private String imageUrl;
}
