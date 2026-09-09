package com.processVisualisation.virtualKitchen.kitchen.dto;

import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response payload returned to clients describing an inventory item's current
 * state, including its kitchen/user scope, item identity and name, quantity,
 * unit, and when it was last updated.
 */
@Data
@Builder
public class InventoryResponseDTO {

    private Long id;
    private Long kitchenId;
    private Long userId;
    private ItemType itemType;
    private Long itemId;
    private String itemName;
    private double quantity;
    private UnitType unit;
    private LocalDateTime lastUpdated;
}
