package com.processVisualisation.virtualKitchen.store.model;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB document tracking the on-hand quantity of a single store item
 * (an {@link Ingredient} or piece of {@link Equipment}, discriminated by
 * {@link ItemType}) for a specific user and kitchen. {@code itemId} refers
 * to the identifier of the corresponding {@link Ingredient} or
 * {@link Equipment} document.
 */
@Data
@Document(collection = "inventory")
public class Inventory {

    public static final String SEQUENCE_NAME = "inventory_sequence";

    @Id
    private Long id;

    @Indexed
    private Long userId;

    @Indexed
    private Long kitchenId;

    private ItemType itemType;
    private Long itemId;

    private double quantity;
    private UnitType unit;

    private LocalDateTime lastUpdated;
}
