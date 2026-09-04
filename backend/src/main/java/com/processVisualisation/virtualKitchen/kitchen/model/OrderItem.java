package com.processVisualisation.virtualKitchen.kitchen.model;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeUnitType;
import com.processVisualisation.virtualKitchen.store.model.ItemType;
import lombok.Data;

@Data
public class OrderItem {

    private Long itemId;
    private ItemType itemType;
    private String itemName;
    private double quantity;
    private RecipeUnitType unit;
    private double price;
    private double subTotal;
}

