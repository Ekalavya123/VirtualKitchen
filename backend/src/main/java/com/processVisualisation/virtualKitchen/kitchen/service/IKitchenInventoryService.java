package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenInventoryRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenInventoryResponseDTO;

import java.util.List;

/**
 * Service contract for managing the association between kitchens and
 * inventory items (kitchen-level inventory allocations): creating an
 * association, listing associations for a kitchen, and removing one.
 */
public interface IKitchenInventoryService {

    /**
     * Creates a new kitchen-inventory association linking a kitchen to an
     * existing inventory record.
     *
     * @param dto the kitchen id and inventory id to associate
     * @return the created association, enriched with the linked item's details
     */
    KitchenInventoryResponseDTO create(KitchenInventoryRequestDTO dto);

    /**
     * Fetches all inventory allocated to the given kitchen, including each
     * associated item's current quantity, unit and display name.
     *
     * @param kitchenId id of the kitchen
     * @return the kitchen's inventory associations
     */
    List<KitchenInventoryResponseDTO> getByKitchen(Long kitchenId);

    /**
     * Deletes the kitchen-inventory association with the given id.
     *
     * @param id id of the association to delete
     */
    void delete(Long id);
}
