package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response payload representing a persisted, reusable recipe step definition,
 * including its generated id, name, description, media and estimated duration.
 */
@Data
@Builder
public class RecipeStepDefinitionResponseDTO {

    private Long id;
    private String name;
    private String description;
    private String mediaUrl;
    private int estimatedTimeSec;
}
