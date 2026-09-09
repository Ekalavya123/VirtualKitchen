package com.processVisualisation.virtualKitchen.store.model;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB document representing an ingredient in the store catalog, with
 * a default {@link UnitType} used when quantities are not otherwise
 * specified. Referenced by identifier from {@link Inventory} and
 * {@link ItemCost} via {@link ItemType#INGREDIENT}.
 */
@Data
@Document(collection = "ingredients")
public class Ingredient {

    public static final String SEQUENCE_NAME = "ingredients_sequence";

    @Id
    private Long id;

    @Indexed(unique = true)
    private String name;

    private String description;

    private UnitType defaultUnit;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}