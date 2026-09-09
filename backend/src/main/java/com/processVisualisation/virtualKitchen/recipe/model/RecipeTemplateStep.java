package com.processVisualisation.virtualKitchen.recipe.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document linking a {@link RecipeStepDefinition} to a
 * {@link RecipeTemplate} at a specific position in its execution order. The
 * compound index enforces a unique step order per template.
 * {@code SEQUENCE_NAME} names the counter used to generate its id.
 */
@Data
@Document(collection = "process_template_step")
@CompoundIndex(name = "template_order_unique", def = "{'processTemplateId':1, 'stepOrder':1}", unique = true)
public class RecipeTemplateStep {

    public static final String SEQUENCE_NAME = "process_template_step_sequence";

    @Id
    private Long id;

    @Indexed
    private Long processTemplateId;

    private Long stepDefinitionId;

    private int stepOrder;
}
