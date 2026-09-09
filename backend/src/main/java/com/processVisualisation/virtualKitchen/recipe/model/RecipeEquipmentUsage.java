package com.processVisualisation.virtualKitchen.recipe.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document recording a piece of equipment's usage during a
 * {@link RecipeExecution}, including how long it was used for and its cost at
 * that time. {@code SEQUENCE_NAME} names the counter used to generate its id.
 */
@Data
@Document(collection = "process_equipment_usage")
public class RecipeEquipmentUsage {

    public static final String SEQUENCE_NAME = "process_equipment_usage_sequence";

    @Id
    private Long id;

    @Indexed
    private Long processExecutionId;

    private Long equipmentId;

    private long usageDurationSec;

    private double costAtTime;
}
