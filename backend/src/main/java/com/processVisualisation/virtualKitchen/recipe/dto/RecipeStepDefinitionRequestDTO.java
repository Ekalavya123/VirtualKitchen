package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

/**
 * Request payload for creating or updating a reusable recipe step definition,
 * capturing its name, description, optional media and estimated duration.
 */
@Data
public class RecipeStepDefinitionRequestDTO {

    private String name;
    private String description;
    private String mediaUrl;
    private int estimatedTimeSec;
}
