package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import lombok.Builder;
import lombok.Data;

/**
 * Represents the AI-generated visualization result for a single recipe step,
 * including the prompt used and the resulting image asset/URL. Nested within
 * {@link RecipeVisualizationResponseDTO}. {@code usedFallback} lets the
 * frontend show that a standard/open-source model was used because premium
 * AI credits were exhausted.
 */
@Data
@Builder
public class RecipeVisualizationStepResponseDTO {
    private String stepId;
    private Long visualizationAssetId;
    private String imagePrompt;
    private String imageUrl;
    private String modelKey;
    private ModelTier modelTier;
    private boolean usedFallback;
}
