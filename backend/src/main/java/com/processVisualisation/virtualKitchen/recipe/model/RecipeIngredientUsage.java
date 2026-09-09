package com.processVisualisation.virtualKitchen.recipe.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document recording an ingredient's usage during a
 * {@link RecipeExecution}, including the quantity used (in a given
 * {@link UnitType}) and its cost at that time. {@code SEQUENCE_NAME} names the
 * counter used to generate its id.
 */
@Data
@Document(collection = "process_ingredient_usage")
public class RecipeIngredientUsage {

    public static final String SEQUENCE_NAME = "process_ingredient_usage_sequence";

    @Id
    private Long id;

    @Indexed
    private Long processExecutionId;

    private Long ingredientId;

    private double quantityUsed;
    private UnitType unit;

    private double costAtTime;
}
