package com.processVisualisation.virtualKitchen.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for generating a recipe execution flow graph, carrying the raw
 * recipe text/description to be parsed into process-flow steps and edges by the
 * AI-driven flow generation feature.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeFlowGenerationRequestDTO {

    @NotBlank(message = "recipe is required")
    private String recipe;

    /**
     * Optional client-generated id (e.g. a UUID) used as the AI request's
     * idempotency key, so an accidental double-submit of the same generation
     * click never reserves/charges credits twice. Safe to omit.
     */
    private String clientRequestId;
}
