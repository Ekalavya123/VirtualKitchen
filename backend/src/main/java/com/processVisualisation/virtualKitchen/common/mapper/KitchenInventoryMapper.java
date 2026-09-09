package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenInventoryRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.KitchenInventoryResponseDTO;
import com.processVisualisation.virtualKitchen.kitchen.model.KitchenInventory;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link KitchenInventory} link entity
 * (associating a kitchen with an inventory row) and its
 * {@link KitchenInventoryRequestDTO}/{@link KitchenInventoryResponseDTO}
 * representations.
 */
@Component
public class KitchenInventoryMapper {

    /**
     * Converts an incoming request DTO into a new {@link KitchenInventory}
     * entity. The entity's {@code id} is left unset, since it is assigned at
     * persistence time.
     *
     * @param dto the request payload describing the kitchen-inventory link to create
     * @return a new, unpersisted {@link KitchenInventory} entity populated from {@code dto}
     */
    public KitchenInventory toEntity(KitchenInventoryRequestDTO dto){
        KitchenInventory ki = new KitchenInventory();
        ki.setKitchenId(dto.getKitchenId());
        ki.setInventoryId(dto.getInventoryId());
        return ki;
    }

    /**
     * Converts a {@link KitchenInventory} entity into its response DTO
     * representation for returning to clients.
     *
     * @param ki the entity to convert
     * @return a fully populated {@link KitchenInventoryResponseDTO}
     */
    public KitchenInventoryResponseDTO toDTO(KitchenInventory ki){
        return KitchenInventoryResponseDTO.builder()
                .id(ki.getId())
                .kitchenId(ki.getKitchenId())
                .inventoryId(ki.getInventoryId())
                .build();
    }
}
