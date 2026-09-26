package com.processVisualisation.virtualKitchen.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for generating a semantic Process structure (MAIN process
 * plus subprocesses) from free-form recipe text via AI. The target recipe id
 * is a path variable on the endpoint, not part of this body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeProcessGenerationRequestDTO {

    @NotBlank(message = "recipeText is required")
    private String recipeText;

    /**
     * Optional client-generated id (e.g. a UUID) used as the AI request's idempotency key, so an
     * accidental double-submit of the same generation click never reserves/charges credits twice.
     */
    private String clientRequestId;
}
