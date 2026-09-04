package com.processVisualisation.virtualKitchen.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class VisualizationResponseDTO {
    private Long RecipeProcessIngredientUsageServiceImplId;
    private String message;
    private List<VisualizationClipResponseDTO> clips;
    private VisualizationClipResponseDTO finalClip;
}
