package com.processVisualisation.virtualKitchen.store.service;

import com.processVisualisation.virtualKitchen.store.dto.EquipmentRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.EquipmentResponseDTO;

import java.util.List;

public interface IEquipmentService {
    EquipmentResponseDTO create(EquipmentRequestDTO dto);
    EquipmentResponseDTO get(Long id);
    List<EquipmentResponseDTO> getAll();
    void delete(Long id);
}
