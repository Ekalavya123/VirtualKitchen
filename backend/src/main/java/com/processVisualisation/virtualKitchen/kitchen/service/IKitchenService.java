package com.processVisualisation.virtualKitchen.kitchen.service;

import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenUpdateDTO;

import java.util.List;

public interface IKitchenService {

    KitchenResponseDTO create(KitchenRequestDTO dto);

    KitchenResponseDTO get(Long id);

    List<KitchenResponseDTO> getByOwner(Long ownerId);

    KitchenResponseDTO update(Long id, KitchenUpdateDTO dto);

    void delete(Long id);
}
