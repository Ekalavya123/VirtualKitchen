package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

@Data
public class RecipeStepDefinitionRequestDTO {

    private String name;
    private String description;
    private String mediaUrl;
    private int estimatedTimeSec;
}
