package com.processVisualisation.virtualKitchen.kitchen.dto;

import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Data;

@Data
public class InventoryRequestDTO {

    private Long userId;
    private Long kitchenId;
    private ItemType itemType;
    private Long itemId;
    private double quantity;
    private UnitType unit;
}
