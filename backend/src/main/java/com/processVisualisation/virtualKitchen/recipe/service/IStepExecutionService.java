package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionUpdateDTO;

import java.util.List;

/**
 * Service contract for creating, updating, and querying the runtime status of individual steps
 * within a recipe (process) execution.
 */
public interface IStepExecutionService {

    /**
     * Creates a new step execution record.
     *
     * @param dto the step execution details to persist (owning process execution id, step, etc.)
     * @return the created step execution
     */
    RecipeStepExecutionResponseDTO create(RecipeStepExecutionRequestDTO dto);

    /**
     * Updates the status and notes of an existing step execution, stamping the start/completion
     * time when the new status represents an in-progress or completed state.
     *
     * @param id  the id of the step execution to update
     * @param dto the new status and notes to apply
     * @return the updated step execution
     */
    RecipeStepExecutionResponseDTO update(Long id, RecipeStepExecutionUpdateDTO dto);

    /**
     * Retrieves all step executions belonging to a given process execution, in creation order.
     *
     * @param processExecutionId the id of the owning process execution
     * @return the step executions for that process execution
     */
    List<RecipeStepExecutionResponseDTO> getByProcess(Long processExecutionId);
}
