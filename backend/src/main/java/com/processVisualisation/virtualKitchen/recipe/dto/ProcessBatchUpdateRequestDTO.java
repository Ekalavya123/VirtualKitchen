package com.processVisualisation.virtualKitchen.recipe.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Request body for {@code PUT /api/v1/recipes/{recipeId}/processes}: a
 * recipe-level save of the complete current editing snapshot — the MAIN
 * process and every SUBPROCESS the Recipe Tool currently has loaded, saved
 * as one logical operation instead of one HTTP call per process. Every item
 * is validated (via {@code ProcessValidator}, same as the single-process
 * update) before any of them are persisted, so a single invalid process
 * rejects the whole batch rather than leaving the recipe half-saved.
 */
@Data
public class ProcessBatchUpdateRequestDTO {

    @NotEmpty
    @Valid
    private List<ProcessBatchItemDTO> processes;
}
