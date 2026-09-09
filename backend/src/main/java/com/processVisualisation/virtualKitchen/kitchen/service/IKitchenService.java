package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenUpdateDTO;

import java.util.List;

/**
 * Service contract for managing {@code Kitchen} entities: creation, lookup
 * by id or owner, updating, and deletion.
 */
public interface IKitchenService {

    /**
     * Creates a new kitchen.
     *
     * @param dto the kitchen's name and owner id
     * @return the created kitchen
     */
    KitchenResponseDTO create(KitchenRequestDTO dto);

    /**
     * Fetches a single kitchen by its id.
     *
     * @param id the kitchen id
     * @return the matching kitchen
     * @throws java.util.NoSuchElementException if no kitchen exists with the given id
     */
    KitchenResponseDTO get(Long id);

    /**
     * Fetches all kitchens owned by the given owner.
     *
     * @param ownerId id of the owning user
     * @return the owner's kitchens
     */
    List<KitchenResponseDTO> getByOwner(Long ownerId);

    /**
     * Updates an existing kitchen's mutable fields (currently its name).
     *
     * @param id  id of the kitchen to update
     * @param dto the new field values
     * @return the updated kitchen
     * @throws java.util.NoSuchElementException if no kitchen exists with the given id
     */
    KitchenResponseDTO update(Long id, KitchenUpdateDTO dto);

    /**
     * Deletes the kitchen with the given id.
     *
     * @param id id of the kitchen to delete
     */
    void delete(Long id);
}
