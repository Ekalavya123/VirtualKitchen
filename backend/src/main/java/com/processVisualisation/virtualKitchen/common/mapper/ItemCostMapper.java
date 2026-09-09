package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.store.model.ItemCost;
import com.processVisualisation.virtualKitchen.store.dto.ItemCostRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.ItemCostResponseDTO;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link ItemCost} entity and its
 * {@link ItemCostRequestDTO}/{@link ItemCostResponseDTO} representations.
 */
@Component
public class ItemCostMapper {

    /**
     * Converts an incoming request DTO into a new {@link ItemCost} entity.
     * The entity's {@code id} and {@code createdAt} are left unset, since
     * they are assigned at persistence time.
     *
     * @param dto the request payload describing the item cost to create
     * @return a new, unpersisted {@link ItemCost} entity populated from {@code dto}
     */
    public ItemCost toEntity(ItemCostRequestDTO dto){
        ItemCost cost = new ItemCost();
        cost.setItemType(dto.getItemType());
        cost.setItemId(dto.getItemId());
        cost.setUnit(dto.getUnit());
        cost.setCostPerUnit(dto.getCostPerUnit());
        cost.setCurrency(dto.getCurrency());
        cost.setEffectiveFrom(dto.getEffectiveFrom());
        return cost;
    }

    /**
     * Converts an {@link ItemCost} entity into its response DTO
     * representation for returning to clients.
     *
     * @param cost the entity to convert
     * @return a fully populated {@link ItemCostResponseDTO}
     */
    public ItemCostResponseDTO toDTO(ItemCost cost){
        return ItemCostResponseDTO.builder()
                .id(cost.getId())
                .itemType(cost.getItemType())
                .itemId(cost.getItemId())
                .unit(cost.getUnit())
                .costPerUnit(cost.getCostPerUnit())
                .currency(cost.getCurrency())
                .effectiveFrom(cost.getEffectiveFrom())
                .createdAt(cost.getCreatedAt())
                .build();
    }
}
