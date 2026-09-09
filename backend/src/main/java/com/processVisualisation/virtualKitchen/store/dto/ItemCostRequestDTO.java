package com.processVisualisation.virtualKitchen.store.dto;

import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Request payload for recording a new cost entry (price per unit and
 * currency, effective from a given date) for an ingredient or equipment
 * item identified by {@link ItemType} and item id.
 */
@Data
public class ItemCostRequestDTO {

    private ItemType itemType;
    private Long itemId;
    private UnitType unit;
    private double costPerUnit;
    private String currency;
    private LocalDateTime effectiveFrom;
}
