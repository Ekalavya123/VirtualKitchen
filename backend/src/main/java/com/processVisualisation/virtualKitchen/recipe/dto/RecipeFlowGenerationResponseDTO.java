package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeFlowGenerationResponseDTO {
    private List<RecipeExecutionStepDTO> steps;
    private List<RecipeExecutionEdgeDTO> edges;
}

