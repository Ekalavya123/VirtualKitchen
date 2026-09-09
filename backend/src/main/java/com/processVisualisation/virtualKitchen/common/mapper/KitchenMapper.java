package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.model.Kitchen;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link Kitchen} entity and its
 * {@link KitchenRequestDTO}/{@link KitchenResponseDTO} representations.
 */
@Component
public class KitchenMapper {

    /**
     * Converts an incoming request DTO into a new {@link Kitchen} entity.
     * The entity's {@code id}, {@code createdAt} and {@code updatedAt} are
     * left unset, since they are assigned at persistence time.
     *
     * @param dto the request payload describing the kitchen to create
     * @return a new, unpersisted {@link Kitchen} entity populated from {@code dto}
     */
    public Kitchen toEntity(KitchenRequestDTO dto){
        Kitchen k = new Kitchen();
        k.setName(dto.getName());
        k.setOwnerId(dto.getOwnerId());
        return k;
    }

    /**
     * Converts a {@link Kitchen} entity into its response DTO representation
     * for returning to clients.
     *
     * @param k the entity to convert
     * @return a fully populated {@link KitchenResponseDTO}
     */
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
