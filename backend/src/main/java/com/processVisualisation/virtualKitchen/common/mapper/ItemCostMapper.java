package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.store.model.ItemCost;
import com.processVisualisation.virtualKitchen.store.dto.ItemCostRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.ItemCostResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ItemCostMapper {

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
