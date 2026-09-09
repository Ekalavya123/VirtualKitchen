package com.processVisualisation.virtualKitchen.kitchen.dto;

import com.processVisualisation.virtualKitchen.store.model.ItemType;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Data;

/**
 * Request payload representing a single requested line item within an
 * {@code OrderCreateRequestDTO}: the item being ordered, the quantity/unit
 * requested, and its unit price.
 */
@Data
public class OrderItemRequestDTO {

    private Long itemId;
    private ItemType itemType;
    private String itemName;
    private double quantity;
    private UnitType unit;
    private double price;
}
