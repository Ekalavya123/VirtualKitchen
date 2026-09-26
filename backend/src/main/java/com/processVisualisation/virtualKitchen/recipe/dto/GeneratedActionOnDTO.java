package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One AI-generated STEP's "Action On" target set: zero or more ingredients
 * and zero or more subprocess references. Subprocess references are
 * temporary, recipe-scoped semantic {@code ref} strings (declared by a
 * {@link GeneratedRecipeProcessDTO} elsewhere in the same generation result), not
 * database ids — the frontend resolves them once real (or client-temporary)
 * process ids exist, mirroring how {@code Process.ProcessNode} never embeds
 * another process as a node.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedActionOnDTO {

    private List<GeneratedActionOnIngredientDTO> ingredients;

    /** Subprocess {@code ref} strings this step's action applies to. */
    private List<String> processes;
}
