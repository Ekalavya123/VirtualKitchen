package com.processVisualisation.virtualKitchen.store.service;

import com.processVisualisation.virtualKitchen.store.dto.ItemCostRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.ItemCostResponseDTO;

public interface IItemCostService {
    ItemCostResponseDTO create(ItemCostRequestDTO dto);
}
