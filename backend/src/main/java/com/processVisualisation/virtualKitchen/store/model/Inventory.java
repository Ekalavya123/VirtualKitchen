package com.processVisualisation.virtualKitchen.store.model;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeUnitType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

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
    private RecipeUnitType unit;

    private LocalDateTime lastUpdated;
}

