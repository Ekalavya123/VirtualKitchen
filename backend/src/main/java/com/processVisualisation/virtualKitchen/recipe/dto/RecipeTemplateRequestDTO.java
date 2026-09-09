package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for creating a new recipe template, capturing its name,
 * description and the id of the user creating it.
 */
@Data
public class RecipeTemplateRequestDTO {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private Long createdBy;
}
