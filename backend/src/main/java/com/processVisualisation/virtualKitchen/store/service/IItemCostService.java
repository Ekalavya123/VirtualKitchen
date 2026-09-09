package com.processVisualisation.virtualKitchen.store.service;

import com.processVisualisation.virtualKitchen.store.dto.ItemCostRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.ItemCostResponseDTO;

/**
 * Service contract for recording cost entries for ingredient or equipment
 * items (price per unit, effective from a given date).
 */
public interface IItemCostService {

    /**
     * Creates a new cost record for an ingredient or equipment item.
     *
     * @param dto the item type, item id, unit, cost per unit, currency and effective date
     * @return the created cost record
     */
    ItemCostResponseDTO create(ItemCostRequestDTO dto);
}
