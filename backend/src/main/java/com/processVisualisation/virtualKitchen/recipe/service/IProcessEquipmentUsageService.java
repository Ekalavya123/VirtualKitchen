package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageResponseDTO;

import java.util.List;

public interface IProcessEquipmentUsageService {

    RecipeEquipmentUsageResponseDTO create(RecipeEquipmentUsageRequestDTO dto);

    List<RecipeEquipmentUsageResponseDTO> getByProcess(Long processExecutionId);
}
