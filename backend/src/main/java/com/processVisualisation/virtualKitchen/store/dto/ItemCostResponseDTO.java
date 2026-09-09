package com.processVisualisation.virtualKitchen.store.dto;

import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response payload representing a stored cost entry for an ingredient or
 * equipment item, including its identifier and creation timestamp.
 */
@Data
@Builder
public class ItemCostResponseDTO {

    private Long id;
    private ItemType itemType;
    private Long itemId;
    private UnitType unit;
    private double costPerUnit;
    private String currency;
    private LocalDateTime effectiveFrom;
    private LocalDateTime createdAt;
}
