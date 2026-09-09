package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepResponseDTO;

import java.util.List;

/**
 * Service contract for creating and retrieving the ordered steps that make up a recipe/process
 * template.
 */
public interface IProcessTemplateStepService {

    /**
     * Creates a new step for a recipe template.
     *
     * @param dto the step details to persist (owning template id, step order, etc.)
     * @return the created step
     */
    RecipeTemplateStepResponseDTO create(RecipeTemplateStepRequestDTO dto);

    /**
     * Retrieves all steps for a given recipe template, in step order.
     *
     * @param processTemplateId the id of the owning template
     * @return the template's steps, ordered by step order
     */
    List<RecipeTemplateStepResponseDTO> getSteps(Long processTemplateId);
}
