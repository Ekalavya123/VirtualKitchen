package com.processVisualisation.virtualKitchen.model;

import lombok.Data;

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
