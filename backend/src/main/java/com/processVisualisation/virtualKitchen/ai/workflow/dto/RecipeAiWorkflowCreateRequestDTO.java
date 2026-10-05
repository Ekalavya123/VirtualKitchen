package com.processVisualisation.virtualKitchen.ai.workflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Starts AI Recipe Creation. {@code recipeText} is required only when the selection includes the
 * recipe process (it is what the process is generated from); visuals/narration alone work on the
 * recipe's existing process.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeAiWorkflowCreateRequestDTO {

    @Valid
    @NotNull(message = "selection is required")
    private RecipeAiTaskSelectionDTO selection;

    private String recipeText;
}
