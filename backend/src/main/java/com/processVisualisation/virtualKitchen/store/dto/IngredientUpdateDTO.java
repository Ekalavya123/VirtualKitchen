package com.processVisualisation.virtualKitchen.store.dto;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeUnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IngredientUpdateDTO {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private RecipeUnitType defaultUnit;
}
