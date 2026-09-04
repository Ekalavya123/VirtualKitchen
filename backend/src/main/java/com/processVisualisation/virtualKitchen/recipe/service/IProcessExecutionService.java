package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionResponseDTO;

import java.util.List;

public interface IProcessExecutionService {

    RecipeExecutionResponseDTO start(RecipeExecutionRequestDTO dto);

    RecipeExecutionResponseDTO updateStatus(Long id, String status);

    List<RecipeExecutionResponseDTO> getByUser(Long userId);
}
