package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.model.Kitchen;
import org.springframework.stereotype.Component;

@Component
public class KitchenMapper {

    public Kitchen toEntity(KitchenRequestDTO dto){
        Kitchen k = new Kitchen();
        k.setName(dto.getName());
        k.setOwnerId(dto.getOwnerId());
        return k;
    }

    public KitchenResponseDTO toDTO(Kitchen k){
        return KitchenResponseDTO.builder()
                .id(k.getId())
                .name(k.getName())
                .ownerId(k.getOwnerId())
                .createdAt(k.getCreatedAt())
                .updatedAt(k.getUpdatedAt())
                .build();
    }
}
