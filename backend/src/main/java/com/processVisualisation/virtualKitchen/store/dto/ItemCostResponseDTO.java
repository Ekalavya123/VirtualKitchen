package com.processVisualisation.virtualKitchen.store.dto;

import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeUnitType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ItemCostResponseDTO {

    private Long id;
    private ItemType itemType;
    private Long itemId;
    private RecipeUnitType unit;
    private double costPerUnit;
    private String currency;
    private LocalDateTime effectiveFrom;
    private LocalDateTime createdAt;
}

