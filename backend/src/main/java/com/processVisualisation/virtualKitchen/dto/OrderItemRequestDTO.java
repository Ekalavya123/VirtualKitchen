package com.processVisualisation.virtualKitchen.dto;

import com.processVisualisation.virtualKitchen.model.ItemType;
import com.processVisualisation.virtualKitchen.model.UnitType;
import lombok.Data;

@Data
public class OrderItemRequestDTO {

    private Long itemId;
    private ItemType itemType;
    private String itemName;
    private double quantity;
    private UnitType unit;
    private double price;
}
