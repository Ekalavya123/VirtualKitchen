package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessIngredientUsageResponseDTO;

import java.util.List;

public interface RecipeProcessIngredientUsageService {

    RecipeProcessIngredientUsageResponseDTO create(RecipeProcessIngredientUsageRequestDTO dto);

    List<RecipeProcessIngredientUsageResponseDTO> getByProcess(Long processExecutionId);
}

