package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for updating an existing recipe template's name and description.
 */
@Data
public class RecipeTemplateUpdateDTO {

    @NotBlank
    private String name;

    private String description;
}
