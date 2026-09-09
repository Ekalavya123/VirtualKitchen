package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageResponseDTO;

import java.util.List;

/**
 * Service contract for recording and querying which pieces of equipment were used during a
 * recipe (process) execution.
 */
public interface IProcessEquipmentUsageService {

    /**
     * Records a new equipment usage entry for a process execution.
     *
     * @param dto the equipment usage details to persist (process execution id, equipment, etc.)
     * @return the created equipment usage record
     */
    RecipeEquipmentUsageResponseDTO create(RecipeEquipmentUsageRequestDTO dto);

    /**
     * Retrieves all equipment usage records for a given process execution.
     *
     * @param processExecutionId the id of the process execution to filter by
     * @return the equipment usage records for that execution
     */
    List<RecipeEquipmentUsageResponseDTO> getByProcess(Long processExecutionId);
}
