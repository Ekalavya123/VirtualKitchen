package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessExecutionResponseDTO;

import java.util.List;

public interface RecipeProcessExecutionService {

    RecipeProcessExecutionResponseDTO start(RecipeProcessExecutionRequestDTO dto);

    RecipeProcessExecutionResponseDTO updateStatus(Long id, String status);

    List<RecipeProcessExecutionResponseDTO> getByUser(Long userId);
}

