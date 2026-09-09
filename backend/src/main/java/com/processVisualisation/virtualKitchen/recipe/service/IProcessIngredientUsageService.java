package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageResponseDTO;

import java.util.List;

/**
 * Service contract for recording and querying which ingredients were consumed during a recipe
 * (process) execution.
 */
public interface IProcessIngredientUsageService {

    /**
     * Records a new ingredient usage entry for a process execution.
     *
     * @param dto the ingredient usage details to persist (process execution id, ingredient, etc.)
     * @return the created ingredient usage record
     */
    RecipeIngredientUsageResponseDTO create(RecipeIngredientUsageRequestDTO dto);

    /**
     * Retrieves all ingredient usage records for a given process execution.
     *
     * @param processExecutionId the id of the process execution to filter by
     * @return the ingredient usage records for that execution
     */
    List<RecipeIngredientUsageResponseDTO> getByProcess(Long processExecutionId);
}
