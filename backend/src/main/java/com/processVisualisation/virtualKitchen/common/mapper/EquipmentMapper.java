package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.store.model.Equipment;
import com.processVisualisation.virtualKitchen.store.dto.EquipmentRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.EquipmentResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class EquipmentMapper {

    public Equipment toEntity(EquipmentRequestDTO dto){
        Equipment e = new Equipment();
        e.setName(dto.getName());
        e.setDescription(dto.getDescription());
        return e;
    }

    public EquipmentResponseDTO toDTO(Equipment e){
        return EquipmentResponseDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
