package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request/response payload for one line item of a recipe's ingredient list,
 * referencing a global ingredient catalog entry by id plus this recipe's own
 * quantity/notes/preparation. Not yet wired to a controller; prepared for a
 * future {@code PUT /recipes/{id}/ingredients}-style endpoint.
 */
@Data
public class RecipeIngredientDTO {

    @NotNull
    private Long ingredientId;

    @NotNull
    private Double quantity;

    @NotNull
    private UnitType unit;

    private String notes;
    private String preparation;
}
