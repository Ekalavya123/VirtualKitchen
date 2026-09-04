package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageResponseDTO;

import java.util.List;

public interface IProcessIngredientUsageService {

    RecipeIngredientUsageResponseDTO create(RecipeIngredientUsageRequestDTO dto);

    List<RecipeIngredientUsageResponseDTO> getByProcess(Long processExecutionId);
}
