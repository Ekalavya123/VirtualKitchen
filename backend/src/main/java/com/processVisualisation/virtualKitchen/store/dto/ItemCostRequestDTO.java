package com.processVisualisation.virtualKitchen.store.dto;

import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeUnitType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ItemCostRequestDTO {

    private ItemType itemType;
    private Long itemId;
    private RecipeUnitType unit;
    private double costPerUnit;
    private String currency;
    private LocalDateTime effectiveFrom;
}

