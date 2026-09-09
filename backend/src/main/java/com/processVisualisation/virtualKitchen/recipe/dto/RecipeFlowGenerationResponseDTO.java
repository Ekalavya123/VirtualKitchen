package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response payload for AI-driven recipe flow generation, containing the full set
 * of steps and edges that make up the generated process-flow graph for a recipe.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeFlowGenerationResponseDTO {

    private List<RecipeExecutionStepDTO> steps;
    private List<RecipeExecutionEdgeDTO> edges;
}
