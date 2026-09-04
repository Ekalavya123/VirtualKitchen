package com.processVisualisation.virtualKitchen.kitchen.dto;

import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeUnitType;
import lombok.Data;

@Data
public class OrderItemRequestDTO {

    private Long itemId;
    private ItemType itemType;
    private String itemName;
    private double quantity;
    private RecipeUnitType unit;
    private double price;
}

