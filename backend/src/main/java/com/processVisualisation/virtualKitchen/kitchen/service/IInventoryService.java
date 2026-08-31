package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.kitchen.dto.InventoryRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.InventoryResponseDTO;

import java.util.List;

public interface IInventoryService {

    InventoryResponseDTO addOrUpdate(InventoryRequestDTO dto);

    List<InventoryResponseDTO> getByUser(Long userId);

    List<InventoryResponseDTO> getByKitchen(Long kitchenId);
}
