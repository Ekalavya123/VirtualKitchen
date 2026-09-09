package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.kitchen.dto.InventoryRequestDTO;
import com.processVisualisation.virtualKitchen.kitchen.dto.InventoryResponseDTO;
import com.processVisualisation.virtualKitchen.store.model.Inventory;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link Inventory} entity and its
 * {@link InventoryRequestDTO}/{@link InventoryResponseDTO} representations.
 */
@Component
public class InventoryMapper {

    /**
     * Converts an incoming request DTO into a new {@link Inventory} entity.
     * The entity's {@code id} and {@code lastUpdated} are left unset — the
     * latter only appears in the response DTO, populated once the inventory
     * row is persisted/updated.
     *
     * @param dto the request payload describing the inventory row to create
     * @return a new, unpersisted {@link Inventory} entity populated from {@code dto}
     */
    public Inventory toEntity(InventoryRequestDTO dto){
        Inventory inv = new Inventory();
        inv.setUserId(dto.getUserId());
        inv.setKitchenId(dto.getKitchenId());
        inv.setItemType(dto.getItemType());
        inv.setItemId(dto.getItemId());
        inv.setQuantity(dto.getQuantity());
        inv.setUnit(dto.getUnit());
        return inv;
    }

    /**
     * Converts an {@link Inventory} entity into its response DTO
     * representation for returning to clients.
     *
     * @param inv the entity to convert
     * @return a fully populated {@link InventoryResponseDTO}
     */
    public InventoryResponseDTO toDTO(Inventory inv){
        return InventoryResponseDTO.builder()
                .id(inv.getId())
                .kitchenId(inv.getKitchenId())
                .userId(inv.getUserId())
                .itemType(inv.getItemType())
                .itemId(inv.getItemId())
                .quantity(inv.getQuantity())
                .unit(inv.getUnit())
                .lastUpdated(inv.getLastUpdated())
                .build();
    }
}
