package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessEquipmentUsageResponseDTO;

import java.util.List;

public interface IProcessEquipmentUsageService {

    ProcessEquipmentUsageResponseDTO create(ProcessEquipmentUsageRequestDTO dto);

    List<ProcessEquipmentUsageResponseDTO> getByProcess(Long processExecutionId);
}
