package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionUpdateDTO;

import java.util.List;

public interface RecipeStepExecutionService {

    RecipeStepExecutionResponseDTO create(RecipeStepExecutionRequestDTO dto);

    RecipeStepExecutionResponseDTO update(Long id, RecipeStepExecutionUpdateDTO dto);

    List<RecipeStepExecutionResponseDTO> getByProcess(Long processExecutionId);
}

