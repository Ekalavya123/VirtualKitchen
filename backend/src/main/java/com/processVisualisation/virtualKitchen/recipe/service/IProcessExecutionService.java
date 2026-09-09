package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionResponseDTO;

import java.util.List;

/**
 * Service contract for starting, transitioning, and querying recipe (process) executions - the
 * runtime instances created when a user runs a recipe/process template.
 */
public interface IProcessExecutionService {

    /**
     * Starts a new recipe execution from the given request.
     *
     * @param dto the details needed to start the execution (user id, template id, etc.)
     * @return the newly created execution
     */
    RecipeExecutionResponseDTO start(RecipeExecutionRequestDTO dto);

    /**
     * Transitions an existing recipe execution to a new status, stamping the completion time
     * when the new status represents completion.
     *
     * @param id     the id of the execution to update
     * @param status the new status, expected to match a {@code RecipeStatus} enum constant name
     * @return the updated execution
     */
    RecipeExecutionResponseDTO updateStatus(Long id, String status);

    /**
     * Retrieves all recipe executions started by a given user.
     *
     * @param userId the id of the user to filter by
     * @return the executions started by that user
     */
    List<RecipeExecutionResponseDTO> getByUser(Long userId);
}
