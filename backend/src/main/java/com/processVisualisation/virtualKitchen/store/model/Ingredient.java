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
 * {@link ItemCost} via {@link ItemType#INGREDIENT}, and from a recipe's
 * ingredient list via
 * {@code com.processVisualisation.virtualKitchen.recipe.model.RecipeIngredient}.
 * {@link #imageUrl} lives here (not on the per-recipe reference) so the same
 * ingredient's image is reused across every recipe rather than duplicated.
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

    private String imageUrl;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}