package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenInventoryRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenInventoryResponseDTO;

import java.util.List;

public interface IKitchenInventoryService {

    KitchenInventoryResponseDTO create(KitchenInventoryRequestDTO dto);

    List<KitchenInventoryResponseDTO> getByKitchen(Long kitchenId);

    void delete(Long id);
}
