package com.processVisualisation.virtualKitchen.store.service;

import com.processVisualisation.virtualKitchen.store.dto.IngredientRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.IngredientResponseDTO;
import com.processVisualisation.virtualKitchen.store.dto.IngredientUpdateDTO;

import java.util.List;

/**
 * Service contract for managing the ingredient catalog: creating, reading,
 * updating and deleting ingredient entries.
 */
public interface IIngredientService {

    /**
     * Creates a new ingredient catalog entry.
     *
     * @param request the ingredient name, description and default unit
     * @return the created ingredient
     * @throws RuntimeException if an ingredient with the same name already exists
     */
    IngredientResponseDTO createIngredient(IngredientRequestDTO request);

    /**
     * Fetches a single ingredient by its identifier.
     *
     * @param id the ingredient identifier
     * @return the matching ingredient
     * @throws RuntimeException if no ingredient exists with the given id
     */
    IngredientResponseDTO getIngredientById(Long id);

    /**
     * Fetches every ingredient in the catalog.
     *
     * @return the list of all ingredients
     */
    List<IngredientResponseDTO> getAllIngredients();

    /**
     * Updates an existing ingredient's name, description and default unit.
     *
     * @param id the identifier of the ingredient to update
     * @param request the replacement values
     * @return the updated ingredient
     * @throws RuntimeException if no ingredient exists with the given id
     */
    IngredientResponseDTO updateIngredient(Long id, IngredientUpdateDTO request);

    /**
     * Deletes an ingredient from the catalog.
     *
     * @param id the identifier of the ingredient to delete
     * @throws RuntimeException if no ingredient exists with the given id
     */
    void deleteIngredient(Long id);
}