package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.kitchen.dto.InventoryRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.InventoryResponseDTO;

import java.util.List;

/**
 * Service contract for managing inventory items that may be scoped to a user
 * or to a kitchen, covering creation/incrementing of stock and lookups by
 * owning user or by kitchen.
 */
public interface IInventoryService {

    /**
     * Creates a new inventory record or, if a matching record already exists,
     * increments its quantity. Implementations should prefer kitchen-scoped
     * matching when a kitchen id is supplied on the request, falling back to
     * user-scoped matching otherwise.
     *
     * @param dto the item being added/updated, including its user/kitchen
     *            scope, item type/id, quantity and unit
     * @return the resulting inventory record's current state
     */
    InventoryResponseDTO addOrUpdate(InventoryRequestDTO dto);

    /**
     * Fetches all inventory items owned directly by the given user.
     *
     * @param userId id of the owning user
     * @return the user's inventory items
     */
    List<InventoryResponseDTO> getByUser(Long userId);

    /**
     * Fetches all inventory items allocated to the given kitchen.
     *
     * @param kitchenId id of the kitchen
     * @return the kitchen's inventory items
     */
    List<InventoryResponseDTO> getByKitchen(Long kitchenId);
}
