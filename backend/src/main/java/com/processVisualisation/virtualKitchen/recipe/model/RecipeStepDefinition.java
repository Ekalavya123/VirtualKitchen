package com.processVisualisation.virtualKitchen.recipe.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document defining a reusable recipe step (name, description, media and
 * estimated duration) that can be attached to one or more recipe templates via
 * {@link RecipeTemplateStep}. {@code SEQUENCE_NAME} names the counter used to
 * generate its id.
 */
@Data
@Document(collection = "step_definition")
public class RecipeStepDefinition {

    public static final String SEQUENCE_NAME = "step_definition_sequence";

    @Id
    private Long id;

    private String name;
    private String description;

    private String mediaUrl;

    private int estimatedTimeSec;
}
