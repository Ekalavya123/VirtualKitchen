package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessIngredientUsageResponseDTO;

import java.util.List;

public interface IProcessIngredientUsageService {

    ProcessIngredientUsageResponseDTO create(ProcessIngredientUsageRequestDTO dto);

    List<ProcessIngredientUsageResponseDTO> getByProcess(Long processExecutionId);
}
