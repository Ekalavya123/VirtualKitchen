package com.processVisualisation.virtualKitchen.recipe.validation;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Outcome of validating a {@code Process} via {@link ProcessValidator},
 * carrying whether it is structurally valid and, if not, the list of
 * validation error messages describing what is wrong.
 */
@Data
@AllArgsConstructor
public class ProcessValidationResult {
    private boolean valid;
    private List<String> errors;
}
