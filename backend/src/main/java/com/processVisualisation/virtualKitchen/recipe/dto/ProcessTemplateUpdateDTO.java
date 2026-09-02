package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ProcessTemplateUpdateDTO {

    @NotBlank
    private String name;

    private String description;
}
