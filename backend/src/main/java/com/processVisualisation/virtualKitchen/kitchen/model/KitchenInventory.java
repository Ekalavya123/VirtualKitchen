package com.processVisualisation.virtualKitchen.kitchen.model;

import lombok.Data;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Domain entity representing the association between a {@code Kitchen} and an
 * inventory item allocated to it, persisted in the "kitchen_inventory" MongoDB
 * collection.
 * <p>
 * This is a join-style record: {@code kitchenId} identifies the owning kitchen and
 * {@code inventoryId} references the underlying inventory record, allowing a single
 * inventory item to be tracked at kitchen scope. The {@code id} is generated from
 * the {@code SEQUENCE_NAME} sequence.
 */
@Data
@Document(collection = "kitchen_inventory")
public class KitchenInventory {

    public static final String SEQUENCE_NAME = "kitchen_inventory_sequence";

    @Id
    private Long id;

    @Indexed
    private Long kitchenId;

    private Long inventoryId;
}
