package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class RecipeProcessTemplateRequestDTO {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private Long createdBy;
}
