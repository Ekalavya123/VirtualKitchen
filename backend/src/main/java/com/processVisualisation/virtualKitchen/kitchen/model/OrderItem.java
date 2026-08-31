package com.processVisualisation.virtualKitchen.kitchen.model;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.model.ItemType;
import lombok.Data;

@Data
public class OrderItem {

    private Long itemId;
    private ItemType itemType;
    private String itemName;
    private double quantity;
    private UnitType unit;
    private double price;
    private double subTotal;
}
