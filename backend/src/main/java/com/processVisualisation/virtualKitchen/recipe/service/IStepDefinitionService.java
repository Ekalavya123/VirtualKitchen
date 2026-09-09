package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionResponseDTO;

import java.util.List;

/**
 * Service contract for creating and listing the reusable catalog of step definitions (e.g.
 * named cooking actions) that recipe template steps and step executions reference.
 */
public interface IStepDefinitionService {

    /**
     * Creates a new step definition.
     *
     * @param dto the step definition details to persist
     * @return the created step definition
     */
    RecipeStepDefinitionResponseDTO create(RecipeStepDefinitionRequestDTO dto);

    /**
     * Retrieves every step definition in the catalog.
     *
     * @return all known step definitions
     */
    List<RecipeStepDefinitionResponseDTO> getAll();
}
