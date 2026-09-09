package com.processVisualisation.virtualKitchen.store.model;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB document recording a price-per-unit entry, in a given currency,
 * for an {@link Ingredient} or piece of {@link Equipment} (discriminated
 * by {@link ItemType}) that becomes effective from a given date. Multiple
 * records per item form a cost history over time.
 */
@Data
@Document(collection = "item_cost")
public class ItemCost {

    public static final String SEQUENCE_NAME = "item_cost_sequence";

    @Id
    private Long id;

    private ItemType itemType;
    private Long itemId;

    private UnitType unit;

    private double costPerUnit;
    private String currency;

    private LocalDateTime effectiveFrom;

    @CreatedDate
    private LocalDateTime createdAt;
}
