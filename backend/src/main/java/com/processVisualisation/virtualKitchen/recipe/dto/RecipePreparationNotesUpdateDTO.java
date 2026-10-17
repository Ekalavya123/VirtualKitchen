package com.processVisualisation.virtualKitchen.recipe.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request body for setting a recipe's default preparation guidance; null or blank resets it to the default. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipePreparationNotesUpdateDTO {

    @Size(max = 4000, message = "preparationNotes must be at most 4000 characters")
    private String preparationNotes;
}
