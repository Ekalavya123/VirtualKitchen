package com.processVisualisation.virtualKitchen.kitchen.dto;

import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Data;

/**
 * Request payload for adding or updating an inventory item, scoped to either a
 * user ({@code userId}) or a kitchen ({@code kitchenId}), identifying the item
 * by its {@code itemType}/{@code itemId} and carrying the quantity and unit
 * being set.
 */
@Data
public class InventoryRequestDTO {

    private Long userId;
    private Long kitchenId;
    private ItemType itemType;
    private Long itemId;
    private double quantity;
    private UnitType unit;
}
