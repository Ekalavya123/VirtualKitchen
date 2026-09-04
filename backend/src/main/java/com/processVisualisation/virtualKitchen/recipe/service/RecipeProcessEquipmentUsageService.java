package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessEquipmentUsageResponseDTO;

import java.util.List;

public interface RecipeProcessEquipmentUsageService {

    RecipeProcessEquipmentUsageResponseDTO create(RecipeProcessEquipmentUsageRequestDTO dto);

    List<RecipeProcessEquipmentUsageResponseDTO> getByProcess(Long processExecutionId);
}

