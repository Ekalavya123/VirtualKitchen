package com.processVisualisation.virtualKitchen.store.dto;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Validated request payload for creating a new ingredient catalog entry,
 * requiring a name and a default {@link UnitType}.
 */
@Data
public class IngredientRequestDTO {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    @NotNull(message = "Default unit is required")
    private UnitType defaultUnit;
}