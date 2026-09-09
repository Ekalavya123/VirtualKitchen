package com.processVisualisation.virtualKitchen.kitchen.model;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.model.ItemType;
import lombok.Data;

/**
 * Embedded value object representing a single line item within an {@code Order}.
 * <p>
 * Captures the ordered item's identity ({@code itemId}, {@code itemType},
 * {@code itemName}), the requested {@code quantity} and its {@code unit}, and the
 * pricing snapshot at order time ({@code price} per unit and the computed
 * {@code subTotal}).
 */
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
