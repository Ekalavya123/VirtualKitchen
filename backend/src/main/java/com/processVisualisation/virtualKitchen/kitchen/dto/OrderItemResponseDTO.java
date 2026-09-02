package com.processVisualisation.virtualKitchen.kitchen.dto;

import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemResponseDTO {

    private Long itemId;
    private ItemType itemType;
    private String itemName;
    private double quantity;
    private UnitType unit;
    private double price;
    private double subTotal;
}
