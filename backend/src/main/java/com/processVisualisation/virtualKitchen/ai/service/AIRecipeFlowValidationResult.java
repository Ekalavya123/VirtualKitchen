package com.processVisualisation.virtualKitchen.ai.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Outcome of validating a recipe execution flow via {@link AIRecipeValidator},
 * carrying whether the flow is structurally valid and, if not, the list of
 * validation error messages describing what is wrong.
 */
@Data
@AllArgsConstructor
public class AIRecipeFlowValidationResult {

    private boolean valid;
    private List<String> errors;
}
