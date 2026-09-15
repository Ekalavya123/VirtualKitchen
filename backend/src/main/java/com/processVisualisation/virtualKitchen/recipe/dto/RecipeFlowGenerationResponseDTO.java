package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response payload for AI-driven recipe flow generation, containing the full set
 * of steps and edges that make up the generated process-flow graph for a recipe,
 * plus which AI model actually produced it (a standard/open-source model is used
 * automatically, with {@code usedFallback} set, when premium credits are exhausted).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeFlowGenerationResponseDTO {

    private List<RecipeExecutionStepDTO> steps;
    private List<RecipeExecutionEdgeDTO> edges;

    private String modelUsed;
    private String modelTier;
    private boolean usedFallback;
    private String fallbackReason;
}
